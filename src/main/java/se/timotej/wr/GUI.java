package se.timotej.wr;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.prefs.Preferences;

public class GUI {
    private static final String PREF_HAN_FILE = "hanFilePath";
    private static final String PREF_TIK_FILE = "tikFilePath";
    private static final String PREF_MONITOR = "monitor";
    private static final String PREF_SEKTION = "sektion";
    private final Preferences prefs = Preferences.userNodeForPackage(GUI.class);

    private JFrame frame;
    private JFileChooser fileChooser;
    private JComboBox<String> sektion;
    private JTextField datum;
    private JTextField hanFil;
    private JTextField tikFil;
    private JCheckBox monitorCheckBox;
    private JButton startButton;
    private JButton pauseButton;
    private JTextArea log;
    private JScrollPane logScrollPane;
    private WRResultGenerator wrResultGenerator;

    public void showGui() {
        // Create and set up the window
        frame = new JFrame("Whippet Race Live-resultat 1.2.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Create components
        sektion = new JComboBox<>(new String[]{"Kalmar", "Karlstad", "Halmstad", "Norrköping", "Södertälje", "Västerås", "Test"});
        sektion.addActionListener(e -> {
            String selectedSektion = (String) sektion.getSelectedItem();
            if ("Test".equals(selectedSektion)) {
                datum.setText("2099-12-31");
                datum.setEnabled(false);
            } else if (!datum.isEnabled() || datum.getText().isEmpty()) {
                LocalDate currentDate = LocalDate.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                datum.setText(currentDate.format(formatter));
                datum.setEnabled(true);
            }
            prefs.put(PREF_SEKTION, selectedSektion);

        });
        datum = new JTextField(10);
        sektion.setSelectedItem(prefs.get(PREF_SEKTION, ""));

        hanFil = new JTextField(60);
        hanFil.setText(prefs.get(PREF_HAN_FILE, ""));
        tikFil = new JTextField(60);
        tikFil.setText(prefs.get(PREF_TIK_FILE, ""));
        JButton browseHanar = new JButton("Browse...");
        JButton browseTikar = new JButton("Browse...");
        monitorCheckBox = new JCheckBox("Kör automatiskt");
        monitorCheckBox.setSelected(prefs.getBoolean(PREF_MONITOR, false));
        monitorCheckBox.addActionListener(e -> prefs.putBoolean(PREF_MONITOR, monitorCheckBox.isSelected()));
        startButton = new JButton("Starta");
        startButton.addActionListener(e -> {
            if (!datum.getText().matches("\\d{4}-\\d{2}-\\d{2}")) {
                setLog("Felaktigt datumformat. Önskat format: åååå-mm-dd");
                return;
            }
            prefs.put(PREF_HAN_FILE, hanFil.getText());
            prefs.put(PREF_TIK_FILE, tikFil.getText());
            startButton.setEnabled(false);
            pauseButton.setEnabled(true);
            try {
                WRResultGenerator wrResultGenerator = new WRResultGenerator(hanFil.getText(), tikFil.getText(), (String) sektion.getSelectedItem(), datum.getText());
                if (monitorCheckBox.isSelected()) {
                    setLog("Kör en första gång...");
                } else {
                    setLog("Kör en gång...");
                }
                new Thread(() -> {
                    try {
                        wrResultGenerator.run();
                        if (monitorCheckBox.isSelected()) {
                            SwingUtilities.invokeAndWait(() -> addLog("Bevakar filerna för uppdateringar (spara excel-filen för att köra igen automatiskt)..."));
                            wrResultGenerator.monitor(() -> SwingUtilities.invokeLater(() -> addLog("Laddat upp uppdaterade resultat")));
                        } else {
                            SwingUtilities.invokeAndWait(() -> {
                                addLog("Färdig");
                                pauseButton.doClick();
                            });
                        }
                    } catch (Exception ex) {
                        StringWriter out = new StringWriter();
                        ex.printStackTrace(new PrintWriter(out));
                        SwingUtilities.invokeLater(() -> {
                            addLog(out.toString());
                            pauseButton.doClick();
                        });
                    }
                }).start();
            } catch (Exception ex) {
                StringWriter out = new StringWriter();
                ex.printStackTrace(new PrintWriter(out));
                addLog(out.toString());
                pauseButton.doClick();
            }
        });
        pauseButton = new JButton("Pausa");
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(e -> {
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
            if (wrResultGenerator != null) {
                wrResultGenerator.stop();
            }
            wrResultGenerator = null;
        });
        fileChooser = new JFileChooser(".");
        log = new JTextArea(10, 20);
        DefaultCaret caret = (DefaultCaret) log.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        // Layout components
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        JPanel sektionDatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sektionDatePanel.add(new JLabel("Sektion: "));
        sektionDatePanel.add(sektion);
        sektionDatePanel.add(new JLabel("Datum: "));
        sektionDatePanel.add(datum);
        frame.add(sektionDatePanel, gbc);

        // First file chooser row
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        frame.add(new JLabel("Excel-fil hanar:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        frame.add(hanFil, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        frame.add(browseHanar, gbc);

        // Second file chooser row
        gbc.gridx = 0;
        gbc.gridy = 2;
        frame.add(new JLabel("Excel-fil tikar:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        frame.add(tikFil, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        frame.add(browseTikar, gbc);

        // Buttons row
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(monitorCheckBox);
        buttonPanel.add(startButton);
        buttonPanel.add(pauseButton);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        frame.add(buttonPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        logScrollPane = new JScrollPane(log);
        frame.add(logScrollPane, gbc);

        // Add action listeners
        addBrowseActionListener(browseHanar, hanFil, PREF_HAN_FILE);
        addBrowseActionListener(browseTikar, tikFil, PREF_TIK_FILE);

        // Final frame setup
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void setLog(String str) {
        log.setText(getTimestamp() + " " + str + "\n");
        scrollDown();
    }

    private void addLog(String str) {
        log.append(getTimestamp() + " " + str + "\n");
        scrollDown();
    }

    private void scrollDown() {
        JScrollBar verticalScrollBar = logScrollPane.getVerticalScrollBar();
        verticalScrollBar.setValue(logScrollPane.getVerticalScrollBar().getMaximum());
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    }

    private void addBrowseActionListener(JButton browseButton, JTextField textField, String prefPath) {
        browseButton.addActionListener(e -> {
            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                String path = fileChooser.getSelectedFile().getAbsolutePath();
                textField.setText(path);
                textField.setCaretPosition(textField.getText().length());
                prefs.put(prefPath, path);
            }
        });
    }
}