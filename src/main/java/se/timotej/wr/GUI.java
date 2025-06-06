package se.timotej.wr;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;

public class GUI {
    private JFrame frame;
    private JFileChooser fileChooser;
    private JTextField hanFil;
    private JTextField tikFil;
    private JButton startButton;
    private JButton pauseButton;
    private JTextArea log;
    private WRResultUploader wrResultUploader;

    public void showGui() {
        // Create and set up the window
        frame = new JFrame("Whippet Race Live-resultat 1.1.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Create components
        hanFil = new JTextField(30);
//        hanFil.setText("/Users/joel/Library/CloudStorage/Dropbox/Whippet Race/WRResultUploader/WR250607_hanar.xlsx");
        tikFil = new JTextField(30);
//        tikFil.setText("/Users/joel/Library/CloudStorage/Dropbox/Whippet Race/WRResultUploader/WR250607_tikar.xlsx");
        JButton browse1 = new JButton("Browse...");
        JButton browse2 = new JButton("Browse...");
        startButton = new JButton("Starta");
        startButton.addActionListener(e -> {
            startButton.setEnabled(false);
            pauseButton.setEnabled(true);
            try {
                WRResultUploader wrResultUploader = new WRResultUploader(hanFil.getText(), tikFil.getText());
                log.setText("Kör en första gång...\n");
                new Thread(() -> {
                    try {
                        wrResultUploader.run();
                        SwingUtilities.invokeAndWait(() -> log.append("Bevakar filerna för uppdateringar (spara excel-filen för att köra igen automatiskt)...\n"));
                        wrResultUploader.monitor(() -> {
                            SwingUtilities.invokeLater(() -> log.append("Laddat upp uppdaterade resultat\n"));
                        });
                    } catch (Exception ex) {
                        StringWriter out = new StringWriter();
                        ex.printStackTrace(new PrintWriter(out));
                        SwingUtilities.invokeLater(() -> {
                            log.append(out.toString());
                            pauseButton.doClick();
                        });
                    }
                }).start();
            } catch (Exception ex) {
                StringWriter out = new StringWriter();
                ex.printStackTrace(new PrintWriter(out));
                log.append(out.toString());
                pauseButton.doClick();
            }
        });
        pauseButton = new JButton("Pausa");
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(e -> {
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
            if (wrResultUploader != null) {
                wrResultUploader.stop();
            }
            wrResultUploader = null;
        });
        fileChooser = new JFileChooser(".");
        log = new JTextArea(5, 20);

        // Layout components
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // First file chooser row
        gbc.gridx = 0;
        gbc.gridy = 0;
        frame.add(new JLabel("Excel-fil hanar:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        frame.add(hanFil, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        frame.add(browse1, gbc);

        // Second file chooser row
        gbc.gridx = 0;
        gbc.gridy = 1;
        frame.add(new JLabel("Excel-fil tikar:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        frame.add(tikFil, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        frame.add(browse2, gbc);

        // Buttons row
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(startButton);
        buttonPanel.add(pauseButton);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        frame.add(buttonPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.add(log);
        frame.add(scrollPane, gbc);


        // Add action listeners
        browse1.addActionListener(e -> {
            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                hanFil.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        browse2.addActionListener(e -> {
            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                tikFil.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        // Final frame setup
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}