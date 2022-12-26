package Seller;


import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

class GUI extends JFrame {
    private final SellerAgent myAgent;

    private final JTextField mileageField;
    private final JTextField priceField;

    GUI(SellerAgent agent) {
        super(agent.getLocalName());

        myAgent = agent;

        JPanel p = new JPanel();
        p.setLayout(new GridLayout(3, 2));
        p.add(new JLabel("Mileage:"));
        mileageField = new JTextField(15);
        p.add(mileageField);
        p.add(new JLabel("Price:"));
        priceField = new JTextField(15);
        p.add(priceField);
        getContentPane().add(p, BorderLayout.CENTER);

        JButton addButton = new JButton("Add");
        addButton.addActionListener(ev -> {
            try {
                String mileage = mileageField.getText().trim();
                String price = priceField.getText().trim();
                myAgent.updateCatalogue(Integer.parseInt(mileage), Integer.parseInt(price));
                mileageField.setText("");
                priceField.setText("");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(GUI.this, "Invalid values. " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        p = new JPanel();
        p.add(addButton);
        getContentPane().add(p, BorderLayout.SOUTH);


        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                myAgent.doDelete();
            }
        });

        setResizable(false);
    }

    public void showGui() {
        pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int centerX = (int) screenSize.getWidth() / 2;
        int centerY = (int) screenSize.getHeight() / 2;
        setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
        super.setVisible(true);
    }
}
