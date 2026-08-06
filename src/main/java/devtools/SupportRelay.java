package devtools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Short-lived, repository-bound support relay for commands that must run in the
 * human desktop session rather than the Codex sandbox.
 *
 * <p>This is intentionally not a remote shell. A request selects one member of
 * {@link Operation}; it cannot supply an executable, argument list, working
 * directory, environment variable, or fragment of PowerShell. The trusted
 * relay process assembles every process invocation from the selected enum and
 * the repository root captured when the Widget starts.</p>
 *
 * <p>The filesystem transport is deliberately simple and inspectable. A client
 * writes a temporary request and atomically renames it into {@code requests/}.
 * The rename is the publication boundary: the relay never observes a partly
 * written request. Results use the same pattern. The random session token
 * prevents requests left by an older relay session from being replayed.</p>
 */
public final class SupportRelay implements AutoCloseable {
    public static final Duration DEFAULT_SESSION_LIFETIME = Duration.ofMinutes(30);
    public static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofMinutes(5);
    public static final String DEFAULT_SANDBOX_ACCOUNT = "CodexSandboxOffline";

    private static final int MAXIMUM_OUTPUT_BYTES = 2_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path repositoryRoot;
    private final Path relayRoot;
    private final Path requestDirectory;
    private final Path resultDirectory;
    private final String sessionToken;
    private final Duration sessionLifetime;
    /*
     * Volatile is sufficient here: the relay has one worker which renews the
     * lease, while the Swing thread and status/clipboard helpers may read it.
     * Replacing the immutable Instant publishes the complete new value.
     */
    private volatile Instant expiresAt;
    private final Consumer<String> statusConsumer;
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread worker;

    public enum Operation {
        GIT_STATUS("git-status", false),
        GIT_DIFF_CHECK("git-diff-check", false),
        MAVEN_TEST("maven-test", true);

        private final String wireName;
        private final boolean writesBuildOutputs;

        Operation(String wireName, boolean writesBuildOutputs) {
            this.wireName = wireName;
            this.writesBuildOutputs = writesBuildOutputs;
        }

        public String wireName() {
            return wireName;
        }

        public boolean writesBuildOutputs() {
            return writesBuildOutputs;
        }

        public static Operation fromWireName(String value) {
            for (Operation operation : values()) {
                if (operation.wireName.equals(value)) {
                    return operation;
                }
            }
            throw new IllegalArgumentException("Unsupported relay operation: " + value);
        }
    }

    public record Request(UUID requestId, String sessionToken, Operation operation) {
    }

    public record CommandPlan(
            Operation operation,
            List<String> invocation,
            Path workingDirectory,
            Duration timeout) {
        public CommandPlan {
            invocation = List.copyOf(invocation);
            workingDirectory = workingDirectory.toAbsolutePath().normalize();
        }
    }

    public SupportRelay(Path repositoryRoot, Consumer<String> statusConsumer)
            throws IOException {
        this(repositoryRoot, statusConsumer, DEFAULT_SESSION_LIFETIME);
    }

    SupportRelay(
            Path repositoryRoot,
            Consumer<String> statusConsumer,
            Duration lifetime) throws IOException {
        this.repositoryRoot = repositoryRoot.toAbsolutePath().normalize();
        this.relayRoot = this.repositoryRoot.resolve(".steadyarc").resolve("relay");
        this.requestDirectory = relayRoot.resolve("requests");
        this.resultDirectory = relayRoot.resolve("results");
        this.sessionToken = newSessionToken();
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("Relay lifetime must be positive");
        }
        this.sessionLifetime = lifetime;
        this.expiresAt = Instant.now().plus(lifetime);
        this.statusConsumer = statusConsumer == null ? ignored -> { } : statusConsumer;
    }

    public Path relayRoot() {
        return relayRoot;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Duration sessionLifetime() {
        return sessionLifetime;
    }

    /**
     * Creates the transport directories, applies the Windows trust boundary,
     * publishes the session description, and starts one polling worker.
     *
     * <p>ACL setup is fail-closed. If Windows cannot resolve the sandbox account
     * or cannot replace the relay directory ACL, the relay never begins
     * listening. Repository permissions alone are intentionally not treated as
     * sufficient authorization for a human-side command broker.</p>
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }
        Files.createDirectories(requestDirectory);
        Files.createDirectories(resultDirectory);
        restrictRelayAcl(relayRoot, DEFAULT_SANDBOX_ACCOUNT);
        restrictRelayAcl(requestDirectory, DEFAULT_SANDBOX_ACCOUNT);
        restrictRelayAcl(resultDirectory, DEFAULT_SANDBOX_ACCOUNT);
        publishSession();

        running.set(true);
        worker = new Thread(this::runLoop, "steadyarc-support-relay");
        worker.setDaemon(true);
        worker.start();
        statusConsumer.accept("Relay listening until " + expiresAt);
    }

    @Override
    public synchronized void close() {
        if (!running.getAndSet(false)) {
            return;
        }
        if (worker != null) {
            worker.interrupt();
        }
        try {
            Files.deleteIfExists(relayRoot.resolve("session.properties"));
        } catch (IOException exception) {
            statusConsumer.accept("Relay closed; session marker cleanup failed: "
                    + exception.getMessage());
        }
        statusConsumer.accept("Relay closed.");
    }

    private void runLoop() {
        try {
            while (running.get() && Instant.now().isBefore(expiresAt)) {
                processAvailableRequests();
                Thread.sleep(250);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            statusConsumer.accept("Relay stopped: " + exception.getMessage());
        } finally {
            close();
        }
    }

    private void processAvailableRequests() throws IOException {
        try (var paths = Files.list(requestDirectory)) {
            for (Path requestPath : paths
                    .filter(SupportRelay::isPublishedRequest)
                    .sorted()
                    .toList()) {
                processOne(requestPath);
                if (!running.get()) {
                    return;
                }
            }
        }
    }

    private void processOne(Path publishedRequest) {
        Path claimed = publishedRequest.resolveSibling(
                publishedRequest.getFileName() + ".processing");
        try {
            /*
             * Claim by rename before parsing. Only one relay worker can rename
             * a published request to this private processing name, which avoids
             * duplicate command execution if polling overlaps or a second
             * Widget instance accidentally points at the same directory.
             */
            Files.move(publishedRequest, claimed, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException alreadyClaimedOrUnavailable) {
            return;
        }

        UUID requestId;
        try {
            requestId = requestIdFromFileName(publishedRequest.getFileName().toString());
        } catch (IllegalArgumentException invalidName) {
            /*
             * An invalid file name is transport noise, not a reason to stop the
             * whole relay. Remove the claimed file and keep serving later valid
             * requests.
             */
            statusConsumer.accept("Rejected relay file with invalid request ID.");
            try {
                Files.deleteIfExists(claimed);
            } catch (IOException ignored) {
                // The relay directory remains short-lived and ignored by Git.
            }
            return;
        }
        Instant started = Instant.now();
        try {
            Request request = readRequest(claimed);
            if (!request.requestId().equals(requestId)) {
                throw new IllegalArgumentException("Request ID does not match its file name");
            }
            if (!constantTimeEquals(sessionToken, request.sessionToken())) {
                throw new IllegalArgumentException("Request belongs to another relay session");
            }

            CommandPlan plan = commandPlan(repositoryRoot, request.operation());
            /*
             * Only a fully parsed, correctly named, authenticated, supported
             * request counts as use. Renewing here prevents malformed files or
             * guessed tokens from keeping the human-side bridge alive. Publish
             * the renewed expiry before starting the command so another client
             * sees the sliding lease even while a long test operation runs.
             */
            renewSessionLease();
            statusConsumer.accept("Running " + request.operation().wireName());
            CommandResult result = execute(plan);
            writeResult(requestId, request.operation(), started, result);
        } catch (Exception exception) {
            writeRejectedResult(requestId, started, exception);
        } finally {
            try {
                Files.deleteIfExists(claimed);
            } catch (IOException exception) {
                statusConsumer.accept("Unable to remove claimed request: "
                        + exception.getMessage());
            }
        }
    }

    synchronized void renewSessionLease() throws IOException {
        Instant previousExpiry = expiresAt;
        expiresAt = Instant.now().plus(sessionLifetime);
        try {
            publishSession();
        } catch (IOException exception) {
            /*
             * Keep memory and the published session marker consistent. A
             * request is rejected rather than executed under an expiry which
             * the sandbox client cannot observe.
             */
            expiresAt = previousExpiry;
            throw exception;
        }
        statusConsumer.accept("Relay lease renewed until " + expiresAt);
    }

    public static Request readRequest(Path path) throws IOException {
        Properties properties = loadProperties(path);
        requireExactKeys(properties, "schemaVersion", "requestId", "sessionToken", "operation");
        if (!"1".equals(properties.getProperty("schemaVersion"))) {
            throw new IllegalArgumentException("Unsupported relay request schema");
        }
        UUID requestId = UUID.fromString(properties.getProperty("requestId"));
        String token = properties.getProperty("sessionToken");
        if (token == null || token.length() < 32) {
            throw new IllegalArgumentException("Invalid relay session token");
        }
        Operation operation = Operation.fromWireName(properties.getProperty("operation"));
        return new Request(requestId, token, operation);
    }

    /**
     * Server-side command catalogue. Request data never participates in these
     * lists, so characters that would be meaningful to cmd.exe or PowerShell
     * cannot become command syntax.
     */
    public static CommandPlan commandPlan(Path repositoryRoot, Operation operation) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        return switch (operation) {
            case GIT_STATUS -> new CommandPlan(
                    operation,
                    List.of("git", "status", "--short", "--branch"),
                    root,
                    DEFAULT_COMMAND_TIMEOUT);
            case GIT_DIFF_CHECK -> new CommandPlan(
                    operation,
                    List.of("git", "diff", "--check"),
                    root,
                    DEFAULT_COMMAND_TIMEOUT);
            case MAVEN_TEST -> new CommandPlan(
                    operation,
                    /*
                     * A Windows batch file needs cmd.exe. Everything after /c
                     * is still relay-owned: neither the request nor clipboard
                     * contributes text. The resolved wrapper path is quoted and
                     * the repository root is fixed when the Widget launches.
                     */
                    List.of(
                            "cmd.exe", "/d", "/s", "/c",
                            "\"\"" + safeWindowsBatchPath(root.resolve("mvnw.cmd"))
                                    + "\" test\""),
                    root,
                    DEFAULT_COMMAND_TIMEOUT);
        };
    }

    private static Path safeWindowsBatchPath(Path path) {
        String value = path.toString();
        if (value.chars().anyMatch(character ->
                character == '"' || character == '&' || character == '|'
                        || character == '<' || character == '>' || character == '^'
                        || character == '%' || character == '!'
                        || character == '\r' || character == '\n')) {
            throw new IllegalArgumentException(
                    "Repository path contains characters unsafe for a batch launcher");
        }
        return path;
    }

    private CommandResult execute(CommandPlan plan) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(plan.invocation())
                .directory(plan.workingDirectory().toFile())
                .redirectErrorStream(true)
                .start();

        CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return readBounded(process);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });

        boolean completed = process.waitFor(
                plan.timeout().toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!completed) {
            /*
             * descendants() matters for wrapper commands: terminating only
             * cmd.exe can leave Maven or Java running after relay cancellation.
             */
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        byte[] output = outputFuture.join();
        return new CommandResult(
                completed ? process.exitValue() : -1,
                !completed,
                new String(output, StandardCharsets.UTF_8));
    }

    private static byte[] readBounded(Process process) throws IOException {
        try (var input = process.getInputStream();
             var output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                int remaining = MAXIMUM_OUTPUT_BYTES - output.size();
                if (remaining > 0) {
                    output.write(buffer, 0, Math.min(read, remaining));
                }
            }
            return output.toByteArray();
        }
    }

    private void publishSession() throws IOException {
        Properties session = new Properties();
        session.setProperty("schemaVersion", "1");
        session.setProperty("sessionToken", sessionToken);
        /*
         * Properties.store escapes backslashes and colons. Base64 keeps the
         * PowerShell client deliberately small and makes repository comparison
         * byte-for-byte instead of dependent on a second properties unescaper.
         */
        session.setProperty("repositoryBase64", Base64.getEncoder().encodeToString(
                repositoryRoot.toString().getBytes(StandardCharsets.UTF_8)));
        session.setProperty("expiresAtUtc", expiresAt.toString());
        session.setProperty("operations", String.join(",",
                java.util.Arrays.stream(Operation.values())
                        .map(Operation::wireName)
                        .toList()));
        atomicStore(relayRoot.resolve("session.properties"), session,
                "Steady Arc support relay session");
    }

    private void writeResult(
            UUID requestId,
            Operation operation,
            Instant started,
            CommandResult result) throws IOException {
        Path outputPath = resultDirectory.resolve(requestId + ".output.log");
        Files.writeString(outputPath, result.output(), StandardCharsets.UTF_8);

        Properties properties = baseResult(requestId, started);
        properties.setProperty("status", result.timedOut() ? "timed-out" : "completed");
        properties.setProperty("operation", operation.wireName());
        properties.setProperty("exitCode", Integer.toString(result.exitCode()));
        properties.setProperty("outputFile", outputPath.getFileName().toString());
        properties.setProperty("outputTruncated",
                Boolean.toString(result.output().getBytes(StandardCharsets.UTF_8).length
                        >= MAXIMUM_OUTPUT_BYTES));
        atomicStore(resultDirectory.resolve(requestId + ".result.properties"),
                properties, "Steady Arc support relay result");
    }

    private void writeRejectedResult(UUID requestId, Instant started, Exception exception) {
        try {
            Properties properties = baseResult(requestId, started);
            properties.setProperty("status", "rejected");
            properties.setProperty("error", safePropertyValue(exception.getMessage()));
            atomicStore(resultDirectory.resolve(requestId + ".result.properties"),
                    properties, "Steady Arc rejected relay request");
        } catch (IOException writeFailure) {
            statusConsumer.accept("Unable to write rejection result: "
                    + writeFailure.getMessage());
        }
    }

    private static Properties baseResult(UUID requestId, Instant started) {
        Properties properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("requestId", requestId.toString());
        properties.setProperty("startedAtUtc", started.toString());
        properties.setProperty("finishedAtUtc", Instant.now().toString());
        return properties;
    }

    private static void atomicStore(Path destination, Properties properties, String comment)
            throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(new java.io.OutputStreamWriter(output, StandardCharsets.UTF_8),
                    comment);
        }
        Files.move(temporary, destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static void requireExactKeys(Properties properties, String... expected) {
        var expectedKeys = java.util.Set.of(expected);
        if (!properties.stringPropertyNames().equals(expectedKeys)) {
            throw new IllegalArgumentException(
                    "Relay request fields must be exactly " + expectedKeys);
        }
    }

    private static boolean isPublishedRequest(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path)
                && name.endsWith(".request.properties")
                && !name.endsWith(".tmp");
    }

    private static UUID requestIdFromFileName(String name) {
        String suffix = ".request.properties";
        if (!name.endsWith(suffix)) {
            throw new IllegalArgumentException("Invalid relay request file name");
        }
        return UUID.fromString(name.substring(0, name.length() - suffix.length()));
    }

    private static String newSessionToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String safePropertyValue(String value) {
        return value == null ? "Rejected" : value.replace('\r', ' ').replace('\n', ' ');
    }

    /**
     * Restricts a relay directory to the Widget owner and the Codex sandbox
     * account. Children inherit the same ACL, so request and result files do not
     * become generally writable merely because the repository is shared.
     */
    static void restrictRelayAcl(Path directory, String sandboxAccount) throws IOException {
        AclFileAttributeView view =
                Files.getFileAttributeView(directory, AclFileAttributeView.class);
        if (view == null) {
            throw new IOException("Filesystem does not support Windows ACLs: " + directory);
        }

        UserPrincipal owner = Files.getOwner(directory);
        UserPrincipalLookupService lookup =
                FileSystems.getDefault().getUserPrincipalLookupService();
        UserPrincipal sandbox;
        try {
            sandbox = lookup.lookupPrincipalByName(sandboxAccount);
        } catch (UserPrincipalNotFoundException exception) {
            throw new IOException("Cannot resolve sandbox account " + sandboxAccount, exception);
        }

        EnumSet<AclEntryPermission> ownerPermissions =
                EnumSet.allOf(AclEntryPermission.class);
        EnumSet<AclEntryPermission> sandboxPermissions = EnumSet.of(
                AclEntryPermission.READ_DATA,
                AclEntryPermission.WRITE_DATA,
                AclEntryPermission.APPEND_DATA,
                AclEntryPermission.READ_NAMED_ATTRS,
                AclEntryPermission.WRITE_NAMED_ATTRS,
                AclEntryPermission.EXECUTE,
                AclEntryPermission.DELETE_CHILD,
                AclEntryPermission.READ_ATTRIBUTES,
                AclEntryPermission.WRITE_ATTRIBUTES,
                AclEntryPermission.DELETE,
                AclEntryPermission.READ_ACL,
                AclEntryPermission.SYNCHRONIZE);
        var inherit = java.util.Set.of(
                AclEntryFlag.FILE_INHERIT,
                AclEntryFlag.DIRECTORY_INHERIT);

        List<AclEntry> acl = new ArrayList<>();
        acl.add(AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(ownerPermissions)
                .setFlags(inherit)
                .build());
        acl.add(AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(sandbox)
                .setPermissions(sandboxPermissions)
                .setFlags(inherit)
                .build());
        view.setAcl(acl);

        // Verify the directory remains usable before advertising the session.
        Files.getFileStore(directory);
        if (!Files.isReadable(directory) || !Files.isWritable(directory)) {
            throw new IOException("Relay ACL made the directory unusable: " + directory);
        }
    }

    private record CommandResult(int exitCode, boolean timedOut, String output) {
    }
}
