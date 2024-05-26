import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.FlowLayout;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class Client extends JFrame {
    private JLabel label;
    private JButton button;
    private JTextArea textArea;

    public Client() {
        setTitle("File Chooser");
        setSize(400, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Initialize the label with default text
        label = new JLabel("Select a file");

        textArea = new JTextArea(5, 20);
        textArea.setSize(280, 300);
        textArea.setEditable(false);
        // JScrollPane scrollPane = new JScrollPane(textArea);

        // Create a button to open the file chooser
        button = new JButton("Choose File");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseFile();
            }
        });

        // Add the components to the frame
        setLayout(new java.awt.FlowLayout());
        add(label);
        // add(scrollPane);
        add(button);
    }

    // Method to open the file chooser and display the selected file path
    private void chooseFile() {
        JScrollPane scrollPane = new JScrollPane(textArea);
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select a File");

        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();
            label.setText("Selected file: " + filePath);
            System.out.println("Selected file path: " + filePath);

            try {
                Socket socket = new Socket("localhost", 9999); // Connect to server
                
                // Sending .zip file to server
                OutputStream out = socket.getOutputStream();
                FileInputStream fis = new FileInputStream(filePath);
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }

                fis.close();
                System.out.println("File sent to server.");

                InputStream input = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                String message = reader.readLine();
                System.out.println("Message from the server: " + message);
                add(scrollPane);
                
                // Receiving .zip file from server
                InputStream in = socket.getInputStream();
                FileOutputStream fos = new FileOutputStream("received_file_from_server.zip");
                while ((bytesRead = in.read(buffer)) >= 0) {
                    fos.write(buffer, 0, bytesRead);
                }
                System.out.println("File received from server.");
                
                remove(button);
                revalidate();
                repaint();
                // label.setText("File received from server with a runtime of " + message + " milliseconds.");
                // label.setText(message);
                String [] parts = message.split(",");
                String text = "";
                for (int i=0; i<parts.length; i++) {
                    text+=parts[i] + "\n";
                }
                
                textArea.setText(text);
                // textArea.append("\nAdditional line of text");
                
                fos.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }


        } else {
            label.setText("File selection canceled");
        }
    }

    public static void main(String[] args) {
        // Run the GUI in the Event Dispatch Thread
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new Client().setVisible(true);
            }
        });
    }
}

