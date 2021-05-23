package Angel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Timer;
import java.util.TimerTask;

class FileWarningMessage extends JFrame implements ActionListener {
    private final int height = 250;
    private final int width = 600;

    private JLabel lMessage = new JLabel("Some Files Did Not Exist. Please Check Your Configuration Files to ensure they're correct.", SwingConstants.CENTER);
    private JLabel lClose = new JLabel("Choose \"Close Bot\" to go and check the files ", SwingConstants.CENTER);
    private JLabel lContinue = new JLabel("Or choose \"Continue\" to continue the boot if you are sure your configuration files are correct.", SwingConstants.CENTER);
    private JLabel lTerminateAuto = new JLabel("The Bot Will Terminate Automatically in 60 seconds", SwingConstants.CENTER);

    private JButton bClose = new JButton("Close Bot");
    private JButton bContinue = new JButton("Continue");

    private boolean continueBoot = false;
    private boolean timerRunning = true;

    private Timer timer = new Timer();

    void startup() {
        Container pane = getContentPane();

        pane.setLayout(new GridLayout(6, 1));

        pane.add(lMessage);
        pane.add(lClose);
        pane.add(lContinue);
        pane.add(lTerminateAuto);

        pane.add(bClose);
        pane.add(bContinue);

        setBounds(500,500, width, height);
        setVisible(true);

        bClose.addActionListener(this);
        bContinue.addActionListener(this);

        timer.schedule(new TimerTask() {
            int timeLeft = 60;
            @Override
            public void run() {
                bClose.setText("Close Bot (" + --timeLeft + ")");
                if (timeLeft == 0) {
                    timerRunning = false;
                    timer.cancel();
                }
            }
        }, 0, 1000);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource().equals(bClose)) {
            continueBoot = false;
            timerRunning = false;
            timer.cancel();
        }
        else if (e.getSource().equals(bContinue)) {
            continueBoot = true;
        }
    }

    boolean isContinueBoot() {
        return continueBoot;
    }

    boolean isTimerRunning() {
        return timerRunning;
    }
}
