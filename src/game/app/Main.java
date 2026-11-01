package game.app;



import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::createWindow);
    }

    private static void createWindow() {
        JFrame window = new JFrame("Master of Reins");
        GamePanel gamePanel = new GamePanel();
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setContentPane(gamePanel);
        window.setExtendedState(JFrame.MAXIMIZED_BOTH);
        window.setVisible(true);
        gamePanel.requestFocusInWindow();
    }
}
