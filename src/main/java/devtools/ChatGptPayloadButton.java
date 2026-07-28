package devtools;

// hello copilot. I want this to be a small one button tool widget
// that places a always on top button on the screen that copies
// "latest snapshot.zip" and "latest test results.log" to the clipboard as files when clicked.
// I want to have it work to paste those files into the chatgpt ui.
// It saves me having to manually find the files and copy them to the clipboard. I want it to be a small java swing app that is always on top and has a single button. When clicked, it should copy the two files to the clipboard as files. I want it to work on windows and mac. I want it to be a single java file that can be compiled and run with javac and java.
// Implement this please


public class ChatGptPayloadButton {
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.JFrame frame = new javax.swing.JFrame("ChatGPT Payload Button");
            frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
            frame.setAlwaysOnTop(true);
            javax.swing.JButton button = new javax.swing.JButton("Copy Files to Clipboard");
            button.addActionListener(e -> copyFilesToClipboard());
            frame.getContentPane().add(button);
            frame.pack();
            frame.setVisible(true);
        });
    }

    private static void copyFilesToClipboard() {
        try {
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            java.awt.datatransfer.Transferable transferable = new java.awt.datatransfer.Transferable() {
                @Override
                public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                    return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.javaFileListFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                    return flavor.equals(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                }

                @Override
                public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) throws java.awt.datatransfer.UnsupportedFlavorException {
                    if (flavor.equals(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                        java.util.List<java.io.File> files = new java.util.ArrayList<>();
                        files.add(new java.io.File("latest snapshot.zip"));
                        files.add(new java.io.File("latest test results.log"));
                        return files;
                    } else {
                        throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
                    }
                }
            };
            clipboard.setContents(transferable, null);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
