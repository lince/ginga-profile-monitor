/*
 * ProfileMonitorView.java
 */

package profilemonitor;

import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.TaskMonitor;
import java.awt.Graphics;
import java.awt.Component;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Timer;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFrame;
//import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.JTableHeader;
//import javax.swing.table.TableModel;
//import java.util.concurrent.locks.Lock;
//import java.util.concurrent.locks.ReentrantLock;

import java.net.*;
import java.io.*;

/**
 * The application's main frame.
 */
public class ProfileMonitorView
        extends FrameView {

    class ProbabilityCellRenderer extends JPanel implements TableCellRenderer {
        private float val;
        private final Border blueBorder = BorderFactory.createLineBorder(Color.BLUE);
        
        ProbabilityCellRenderer() {
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus, int row,
                int col) {
            setBackground((Color) Color.BLACK);
            val = ((Float)value).floatValue();
            if (isSelected) {
                setBorder(blueBorder);
            } else {
                setBorder(BorderFactory.createEmptyBorder());
            }
            return this;
        }
        
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.GREEN);
            g.fillRect(2, 2, (int)(val * this.getWidth()) - 4, this.getHeight() - 4);
        }
    }

    class ProfileCellRenderer extends JPanel implements TableCellRenderer {
        private float[] profile;
        private final Border blueBorder = BorderFactory.createLineBorder(Color.BLUE);

        ProfileCellRenderer() {
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus, int row,
                int col) {
            profile = (float[]) value;
            setBackground((Color) Color.BLACK);
            if (isSelected) {
                setBorder(blueBorder);
            } else {
                setBorder(BorderFactory.createEmptyBorder());
            }
            return this;
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            /*// Color profile
            int w = (int) (((float) this.getWidth() - 2) / profile.length);
            int r = this.getWidth() - profile.length * w;
            int x = 1 + r/2;
            for (int i = 0; i < profile.length; i ++) {
                g.setColor(new Color((int)(255 * profile[i]), 0, 0));
                g.fillRect(x, 2, w, this.getHeight() - 4);
                x += w;
            }*/
            
            
            // bar chart profile
            int w = (int) (((float) this.getWidth() - 2) / profile.length);
            int r = this.getWidth() - profile.length * w;
            int x = 1 + r/2;
            int middle = (int)(0.5 + this.getHeight() / 2);
            //g.setColor(new Color(180,180,126)); 
            g.setColor(new Color(100,100,100)); 
            g.drawLine(3, middle, this.getWidth() - 3, middle);
            //g.setColor(Color.YELLOW);
            g.setColor(Color.RED);
            for (int i = 0; i < profile.length; i ++) {
                g.fillRect(x, (int) (middle - profile[i] * middle) + 1, w, (int)(profile[i] * middle * 2));
                x += w;
            }
        }
    }

    class ToolTipHeader extends JTableHeader {

        String[] toolTips;

        public ToolTipHeader(TableColumnModel model) {
            super(model);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int col = columnAtPoint(e.getPoint());
            int modelCol = getTable().convertColumnIndexToModel(col);
            String retStr;
            try {
                retStr = toolTips[modelCol];
            } catch (NullPointerException ex) {
                retStr = "";
            } catch (ArrayIndexOutOfBoundsException ex) {
                retStr = "";
            }
            if (retStr.length() < 1) {
                retStr = super.getToolTipText(e);
            }
            return retStr;
        }

        public void setToolTipStrings(String[] toolTips) {
            this.toolTips = toolTips;
        }
    }

    public ProfileMonitorView(SingleFrameApplication app) {
        super(app);

        initComponents();

        // status bar initialization - message timeout, idle icon and busy animation, etc
        ResourceMap resourceMap = getResourceMap();
        int messageTimeout = resourceMap.getInteger("StatusBar.messageTimeout");
        messageTimer = new Timer(messageTimeout, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                statusMessageLabel.setText("");
            }
        });
        messageTimer.setRepeats(false);
        int busyAnimationRate = resourceMap.getInteger("StatusBar.busyAnimationRate");
        for (int i = 0; i < busyIcons.length; i++) {
            busyIcons[i] = resourceMap.getIcon("StatusBar.busyIcons[" + i + "]");
        }
        busyIconTimer = new Timer(busyAnimationRate, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                busyIconIndex = (busyIconIndex + 1) % busyIcons.length;
                statusAnimationLabel.setIcon(busyIcons[busyIconIndex]);
            }
        });
        idleIcon = resourceMap.getIcon("StatusBar.idleIcon");
        statusAnimationLabel.setIcon(idleIcon);
        progressBar.setVisible(false);

        // connecting action tasks to status bar via TaskMonitor
        TaskMonitor taskMonitor = new TaskMonitor(getApplication().getContext());
        taskMonitor.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();
                if ("started".equals(propertyName)) {
                    if (!busyIconTimer.isRunning()) {
                        statusAnimationLabel.setIcon(busyIcons[0]);
                        busyIconIndex = 0;
                        busyIconTimer.start();
                    }
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);
                } else if ("done".equals(propertyName)) {
                    busyIconTimer.stop();
                    statusAnimationLabel.setIcon(idleIcon);
                    progressBar.setVisible(false);
                    progressBar.setValue(0);
                } else if ("message".equals(propertyName)) {
                    String text = (String)(evt.getNewValue());
                    statusMessageLabel.setText((text == null) ? "" : text);
                    messageTimer.restart();
                } else if ("progress".equals(propertyName)) {
                    int value = (Integer)(evt.getNewValue());
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(value);
                }
            }
        });

        // start update button
        jbutUpdate.setEnabled(false);
        // strIP = "192.168.10.133";
        strIP = "127.0.0.1";
        strPort = "8183";

        // set custom renderers
        TableColumnModel tcm = jTable1.getColumnModel();
        TableColumn column = tcm.getColumn(tcm.getColumnCount() - 2);
        TableCellRenderer renderer = new ProbabilityCellRenderer();
        column.setCellRenderer(renderer);

        column = tcm.getColumn(tcm.getColumnCount() - 3);
        TableCellRenderer renderer2 = new ProfileCellRenderer();
        column.setCellRenderer(renderer2);

        TableColumnModel tcm2 = jTable2.getColumnModel();
        TableColumn column2 = tcm2.getColumn(tcm2.getColumnCount() - 1);
        TableCellRenderer renderer3 = new ProfileCellRenderer();
        column2.setCellRenderer(renderer3);
        
        // set tooltips
        String profHelp =
                "<html>Set of attributes used to recongnize the user.<p>"
                + "In this demo, the profile is composed of<p>"
                + "the following attributes:<p>"
                + "- Others<p>"
                + "- Music<p>"
                + "- News<p>"
                + "- Romance<p>"
                + "- Kids<p>"
                + "- Action<p>"
                + "- Soap opera<p>"
                + "- Series<p>"
                + "- Sports<p>"
                + "- Comedy<p>"
                + "- Movies";
        String[] toolTipStr = { "User ID",
                                "User name",
                                "User age",
                                "User ZIP code",
                                "User gender",
                                "Profile ID",
                                "Is user logged in?",
                                profHelp,
                                "<html>Largest profile probability <p>when compared to candidates",
                                "Most similar candidate"};

        ToolTipHeader header = new ToolTipHeader(tcm);
        header.setToolTipStrings(toolTipStr);
        header.setToolTipText("Default ToolTip TEXT");
        jTable1.setTableHeader(header);

        //ToolTipHeader header2 = new ToolTipHeader(tcm2);
        //header2.setToolTipStrings(toolTipStr);
        //header2.setToolTipText("Default ToolTip TEXT");
        //jTable2.setTableHeader(header2);


        // start timer in not automatic updating
        jcboxUpdate.setSelected(false);
        startUTimer();
    }

    @Action
    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = ProfileMonitorApp.getApplication().getMainFrame();
            aboutBox = new ProfileMonitorAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        ProfileMonitorApp.getApplication().show(aboutBox);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jtextGSGenre = new javax.swing.JTextField();
        jtextService = new javax.swing.JTextField();
        jtextChannel = new javax.swing.JTextField();
        jPanel2 = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jtextGuess = new javax.swing.JTextField();
        jtextLogged = new javax.swing.JTextField();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTable2 = new javax.swing.JTable();
        jbutUpdate = new javax.swing.JButton();
        jcboxUpdate = new javax.swing.JCheckBox();
        jPanel4 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        menuBar = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        javax.swing.JMenu helpMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();
        statusPanel = new javax.swing.JPanel();
        javax.swing.JSeparator statusPanelSeparator = new javax.swing.JSeparator();
        statusMessageLabel = new javax.swing.JLabel();
        statusAnimationLabel = new javax.swing.JLabel();
        progressBar = new javax.swing.JProgressBar();
        jdiaSettings = new javax.swing.JDialog();
        jbutOk = new javax.swing.JButton();
        jbutCancel = new javax.swing.JButton();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jtextIP = new javax.swing.JTextField();
        jtextPort = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();

        mainPanel.setName("mainPanel"); // NOI18N
        mainPanel.setPreferredSize(new java.awt.Dimension(527, 600));

        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(profilemonitor.ProfileMonitorApp.class).getContext().getResourceMap(ProfileMonitorView.class);
        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel1.border.title"))); // NOI18N
        jPanel1.setName("jPanel1"); // NOI18N

        jLabel1.setText(resourceMap.getString("jLabel1.text")); // NOI18N
        jLabel1.setName("jLabel1"); // NOI18N

        jLabel2.setText(resourceMap.getString("jLabel2.text")); // NOI18N
        jLabel2.setName("jLabel2"); // NOI18N

        jLabel3.setText(resourceMap.getString("jLabel3.text")); // NOI18N
        jLabel3.setName("jLabel3"); // NOI18N

        jtextGSGenre.setEditable(false);
        jtextGSGenre.setText(resourceMap.getString("jtextGSGenre.text")); // NOI18N
        jtextGSGenre.setName("jtextGSGenre"); // NOI18N

        jtextService.setEditable(false);
        jtextService.setName("jtextService"); // NOI18N

        jtextChannel.setEditable(false);
        jtextChannel.setName("jtextChannel"); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3)
                    .addComponent(jLabel1))
                .addGap(4, 4, 4)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jtextGSGenre)
                    .addComponent(jtextService)
                    .addComponent(jtextChannel, javax.swing.GroupLayout.DEFAULT_SIZE, 149, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jtextChannel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jtextService, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(jtextGSGenre, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(28, Short.MAX_VALUE))
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel2.border.title"))); // NOI18N
        jPanel2.setName("jPanel2"); // NOI18N

        jLabel4.setText(resourceMap.getString("jLabel4.text")); // NOI18N
        jLabel4.setToolTipText(resourceMap.getString("jLabel4.toolTipText")); // NOI18N
        jLabel4.setName("jLabel4"); // NOI18N

        jLabel5.setText(resourceMap.getString("jLabel5.text")); // NOI18N
        jLabel5.setToolTipText(resourceMap.getString("jLabel5.toolTipText")); // NOI18N
        jLabel5.setName("jLabel5"); // NOI18N

        jLabel6.setName("jLabel6"); // NOI18N

        jtextGuess.setEditable(false);
        jtextGuess.setName("jtextGuess"); // NOI18N

        jtextLogged.setEditable(false);
        jtextLogged.setName("jtextLogged"); // NOI18N

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel5)
                    .addComponent(jLabel6)
                    .addComponent(jLabel4))
                .addGap(4, 4, 4)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jtextGuess)
                    .addComponent(jtextLogged, javax.swing.GroupLayout.PREFERRED_SIZE, 59, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jtextLogged, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(jtextGuess, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel6)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel3.border.title"))); // NOI18N
        jPanel3.setName("jPanel3"); // NOI18N

        jScrollPane2.setName("jScrollPane2"); // NOI18N
        jScrollPane2.setPreferredSize(new java.awt.Dimension(200, 403));

        jTable2.setModel(new javax.swing.table.DefaultTableModel(
            null,
            new String [] {
                "CID",
                "Profile"
            }
        ) {
            Class[] types = new Class [] {
                // java.lang.Integer.class, java.lang.Integer.class, java.lang.String.class, java.lang.Object.class, java.lang.Object.class
                java.lang.Integer.class,
                java.lang.Object.class
            };
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jTable2.setName("jTable2"); // NOI18N
        jTable2.setShowHorizontalLines(false);
        jTable2.setShowVerticalLines(false);
        jScrollPane2.setViewportView(jTable2);

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 314, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 190, Short.MAX_VALUE)
                .addContainerGap())
        );

        jbutUpdate.setText(resourceMap.getString("jbutUpdate.text")); // NOI18N
        jbutUpdate.setActionCommand(resourceMap.getString("jbutUpdate.actionCommand")); // NOI18N
        jbutUpdate.setName("jbutUpdate"); // NOI18N
        jbutUpdate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbutUpdateActionPerformed(evt);
            }
        });

        jcboxUpdate.setSelected(true);
        jcboxUpdate.setText(resourceMap.getString("jcboxUpdate.text")); // NOI18N
        jcboxUpdate.setName("jcboxUpdate"); // NOI18N
        jcboxUpdate.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                CBhandler(evt);
            }
        });

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel4.border.title"))); // NOI18N
        jPanel4.setName("jPanel4"); // NOI18N

        jScrollPane1.setName("jScrollPane1"); // NOI18N

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            null,
            new String [] {
                "UID",
                "Name",
                "Age",
                "ZIP",
                "Gender",
                "PID",
                "Status",
                "Profile",
                "Probability",
                "MSC"
            }
        ) {
            Class[] types = new Class [] {
                // java.lang.Integer.class, java.lang.Integer.class, java.lang.String.class, java.lang.Object.class, java.lang.Object.class
                java.lang.Integer.class,
                java.lang.String.class,
                java.lang.Integer.class,
                java.lang.String.class,
                java.lang.String.class,
                java.lang.Integer.class,
                java.lang.String.class,
                java.lang.Object.class,
                java.lang.Float.class,
                java.lang.Integer.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false,
                false, false, false, false,
                false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jTable1.setName("jTable1"); // NOI18N
        jTable1.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jTable1.setShowHorizontalLines(false);
        jTable1.setShowVerticalLines(false);
        jScrollPane1.setViewportView(jTable1);

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 649, Short.MAX_VALUE)
            .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel4Layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 625, Short.MAX_VALUE)
                    .addContainerGap()))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 218, Short.MAX_VALUE)
            .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel4Layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 194, Short.MAX_VALUE)
                    .addContainerGap()))
        );

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, mainPanelLayout.createSequentialGroup()
                .addGap(26, 26, 26)
                .addComponent(jcboxUpdate)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 284, Short.MAX_VALUE)
                .addComponent(jbutUpdate)
                .addGap(62, 62, 62))
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jbutUpdate)
                    .addComponent(jcboxUpdate, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(654, 654, 654))
        );

        menuBar.setName("menuBar"); // NOI18N

        jMenu1.setText(resourceMap.getString("jMenu1.text")); // NOI18N
        jMenu1.setName("jMenu1"); // NOI18N

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(profilemonitor.ProfileMonitorApp.class).getContext().getActionMap(ProfileMonitorView.class, this);
        jMenuItem1.setAction(actionMap.get("showSettings")); // NOI18N
        jMenuItem1.setText(resourceMap.getString("jMenuItem1.text")); // NOI18N
        jMenuItem1.setName("jMenuItem1"); // NOI18N
        jMenu1.add(jMenuItem1);

        menuBar.add(jMenu1);

        helpMenu.setText(resourceMap.getString("helpMenu.text")); // NOI18N
        helpMenu.setName("helpMenu"); // NOI18N

        aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
        aboutMenuItem.setName("aboutMenuItem"); // NOI18N
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        statusPanel.setName("statusPanel"); // NOI18N

        statusPanelSeparator.setName("statusPanelSeparator"); // NOI18N

        statusMessageLabel.setName("statusMessageLabel"); // NOI18N

        statusAnimationLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        statusAnimationLabel.setName("statusAnimationLabel"); // NOI18N

        progressBar.setName("progressBar"); // NOI18N

        javax.swing.GroupLayout statusPanelLayout = new javax.swing.GroupLayout(statusPanel);
        statusPanel.setLayout(statusPanelLayout);
        statusPanelLayout.setHorizontalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(statusMessageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 659, Short.MAX_VALUE)
                .addComponent(statusAnimationLabel)
                .addContainerGap())
            .addComponent(statusPanelSeparator, javax.swing.GroupLayout.DEFAULT_SIZE, 683, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, statusPanelLayout.createSequentialGroup()
                .addContainerGap(402, Short.MAX_VALUE)
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(133, 133, 133))
        );
        statusPanelLayout.setVerticalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, statusPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(statusPanelSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(statusMessageLabel)
                        .addComponent(statusAnimationLabel))
                    .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        jdiaSettings.setTitle(resourceMap.getString("jdiaSettings.title")); // NOI18N
        jdiaSettings.setName("jdiaSettings"); // NOI18N
        jdiaSettings.setResizable(false);

        jbutOk.setAction(actionMap.get("settingsSetValues")); // NOI18N
        jbutOk.setText(resourceMap.getString("jbutOk.text")); // NOI18N
        jbutOk.setName("jbutOk"); // NOI18N

        jbutCancel.setAction(actionMap.get("closeSettings")); // NOI18N
        jbutCancel.setText(resourceMap.getString("jbutCancel.text")); // NOI18N
        jbutCancel.setName("jbutCancel"); // NOI18N

        jLabel7.setText(resourceMap.getString("jLabel7.text")); // NOI18N
        jLabel7.setName("jLabel7"); // NOI18N

        jLabel8.setText(resourceMap.getString("jLabel8.text")); // NOI18N
        jLabel8.setName("jLabel8"); // NOI18N

        jtextIP.setText(resourceMap.getString("jtextIP.text")); // NOI18N
        jtextIP.setName("jtextIP"); // NOI18N

        jtextPort.setText(resourceMap.getString("jtextPort.text")); // NOI18N
        jtextPort.setName("jtextPort"); // NOI18N

        jLabel9.setText(resourceMap.getString("jLabel9.text")); // NOI18N
        jLabel9.setName("jLabel9"); // NOI18N

        javax.swing.GroupLayout jdiaSettingsLayout = new javax.swing.GroupLayout(jdiaSettings.getContentPane());
        jdiaSettings.getContentPane().setLayout(jdiaSettingsLayout);
        jdiaSettingsLayout.setHorizontalGroup(
            jdiaSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jdiaSettingsLayout.createSequentialGroup()
                .addGroup(jdiaSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jdiaSettingsLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel9))
                    .addGroup(jdiaSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jdiaSettingsLayout.createSequentialGroup()
                            .addGap(67, 67, 67)
                            .addComponent(jbutOk, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(jbutCancel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jdiaSettingsLayout.createSequentialGroup()
                            .addGap(56, 56, 56)
                            .addGroup(jdiaSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(jLabel7)
                                .addGroup(jdiaSettingsLayout.createSequentialGroup()
                                    .addComponent(jLabel8)
                                    .addGap(3, 3, 3)))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(jdiaSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(jtextPort, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(jtextIP, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jdiaSettingsLayout.setVerticalGroup(
            jdiaSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jdiaSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel9)
                .addGroup(jdiaSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jdiaSettingsLayout.createSequentialGroup()
                        .addGap(30, 30, 30)
                        .addComponent(jLabel7))
                    .addGroup(jdiaSettingsLayout.createSequentialGroup()
                        .addGap(18, 18, 18)
                        .addComponent(jtextIP, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jdiaSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jtextPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8))
                .addGap(18, 18, 18)
                .addGroup(jdiaSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jbutOk)
                    .addComponent(jbutCancel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        setComponent(mainPanel);
        setMenuBar(menuBar);
        setStatusBar(statusPanel);
    }// </editor-fold>//GEN-END:initComponents

    private void jbutUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbutUpdateActionPerformed
        fillComponents();
    }//GEN-LAST:event_jbutUpdateActionPerformed

    private void CBhandler(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_CBhandler
        if (evt.getStateChange() == java.awt.event.ItemEvent.DESELECTED)
            jbutUpdate.setEnabled(true);
        else
            jbutUpdate.setEnabled(false);
    }//GEN-LAST:event_CBhandler

    // connect to server using socket
    private Socket connect() {
        // connect to server using IP
        Socket socket;
        try {
            socket = new Socket(strIP, Integer.parseInt(strPort));

        } catch(Exception e) {
            /*JOptionPane.showMessageDialog(null,
                    e.toString() + ": " + strIP + " " + strPort
                    , "Socket connection error", 0);*/
            statusMessageLabel.setText("Error while connecting: " + e.toString() + " (" + strIP + ":" + strPort + ")");
            jcboxUpdate.setSelected(false);
            return null;
        }
        statusMessageLabel.setText("Successfully connected.");
        return socket;
    }

    // disconnect to server using socket
    private void disconnect(Socket socket) {
        try {
            socket.close();
            statusMessageLabel.setText("Successfully disconnected.");
        } catch(IOException ioe) {
            /*JOptionPane.showMessageDialog(null, ioe.toString(),
                    "Error while closing socket", 0);*/
            statusMessageLabel.setText("Error while disconnecting: " + ioe.toString());
        }
    }

    private void fillComponents() {
    	String msg = "", emsg = "";
    	
        // connect to server
        Socket socket = connect();
        if (socket == null)
            return;

        // exchange data with server
        try {
        	BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        	DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        	// send debug message to server 	
            out.writeBytes("d");
        	// get answer 	
            msg = in.readLine();
            emsg = in.readLine();
            
            in.close();
            out.close();

        } catch(Exception e) {
            /* JOptionPane.showMessageDialog(null, e.toString(),
                    "Error while communicating with server", 0); */
            jcboxUpdate.setSelected(false);
            statusMessageLabel.setText("Error while communicating: " + e.toString());
        }    
        
        // disconnect from server
        disconnect(socket);
        
        if (emsg.compareTo("NOERR") != 0){
        	// JOptionPane.showMessageDialog(null, "NOERR", "Error Message", JOptionPane.INFORMATION_MESSAGE);
            statusMessageLabel.setText("");
        	return;	
        }

        if (msg.length() < 2){
        	// JOptionPane.showMessageDialog(null, "Empty (no changes in server)", "Message", JOptionPane.INFORMATION_MESSAGE);
            statusMessageLabel.setText("");
        	return;	
        }
        
        statusMessageLabel.setText("Updating...");
      
        String str[] = msg.split("\\&");           
        String strSplited[];
        String strSplited2[];
        
        
        //JOptionPane.showConfirmDialog(null, str, "Message", JOptionPane.OK_OPTION);
        
        /*if (true)
        	return;
        */
        
        System.out.println("[" + msg + "]");
        
        // fill in fields
        strSplited = str[0].split("\\=");    
        jtextLogged.setText(strSplited[1]);
        
        strSplited = str[1].split("\\=");
        jtextGuess.setText(strSplited[1]);

        strSplited = str[2].split("\\=");
        int numCandidates =  Integer.parseInt(strSplited[1]);
        
        strSplited = str[3].split("\\=");        
        jtextChannel.setText(strSplited[1]);

        strSplited = str[4].split("\\=");  
        jtextService.setText(strSplited[1]);

        strSplited = str[5].split("\\=");  
        jtextGSGenre.setText(strSplited[1]);
       
        strSplited = str[6].split("\\=");
        jtextGSGenre.setText(jtextGSGenre.getText()+"/"+strSplited[1]);        

  
        DefaultTableModel model = (DefaultTableModel)jTable1.getModel();
        //model.getDataVector().removeAllElements();
        while (model.getRowCount() > 0)
            model.removeRow(0);

        DefaultTableModel model2 = (DefaultTableModel)jTable2.getModel();
        //model2.getDataVector().removeAllElements();
        while (model2.getRowCount() > 0)
            model2.removeRow(0);

        int msgLine = 7;

        for (int i = 0; i < numCandidates; i++){
            model2.addRow(new String[ model2.getColumnCount() ]);
            jTable2.setValueAt(i, i, 0);

            strSplited = str[msgLine].split("\\=");
            msgLine++;

            strSplited2 = strSplited[1].split("\\,");

            float profCandidate[] = new float[strSplited2.length];

            float max = 0;
            float val = 0.0f;
            
            for (int j = 0; j < strSplited2.length; j++ ){
                
                val = Float.parseFloat(strSplited2[j]);
	        profCandidate[j] = val;
	        if (val > max)
	           	 max = val;
            }

            for (int j = 0; j < strSplited2.length; j++ )
	         profCandidate[j] = profCandidate[j] / max;

            jTable2.setValueAt(profCandidate, i, 1);
        }

        //msgLine++;
//JOptionPane.showConfirmDialog(null, "xxx", "Message", JOptionPane.OK_OPTION);

        int i = 0;
               
        do {      	          	
        	//JOptionPane.showMessageDialog(null, str[msgLine], "?", JOptionPane.INFORMATION_MESSAGE);            	
            strSplited = str[msgLine].split("\\=");    
            msgLine++;
                        	
            strSplited2 = strSplited[1].split("\\,");                
            String uid = strSplited2[0];
            String name = strSplited2[1];
            String age = strSplited2[2];
            String zip = strSplited2[3];
            String sex = strSplited2[4]; 	

            if (uid.compareTo(jtextLogged.getText()) == 0){
            	jtextLogged.setText(name);
            }
            
            String guessName = jtextGuess.getText().split("\\:")[0];
            if (uid.compareTo(guessName) == 0){
            	jtextGuess.setText( name + ":" + jtextGuess.getText().split("\\:")[1]);            	
            }

            
            int count = 0;                
            
            do {                		            	
                strSplited = str[msgLine].split("\\=");    	            		            	
                if (strSplited[0].substring(0,10).compareTo("debug.prob")==0)
                	break;                    
                msgLine++;                    
                
            	model.addRow(new String[ model.getColumnCount() ]);	                
                jTable1.setValueAt(Integer.parseInt(uid), i, 0);
                jTable1.setValueAt(name, i, 1);
                jTable1.setValueAt(Integer.parseInt(age), i, 2);
                jTable1.setValueAt(zip, i, 3);
                jTable1.setValueAt(sex, i, 4);
                                              
                jTable1.setValueAt(Integer.parseInt(strSplited[0].substring(13)), i, 5);
         
                if (name.compareTo(jtextLogged.getText()) == 0)
                	jTable1.setValueAt("logged", i, 6);
                else
                	jTable1.setValueAt("", i, 6); 
                
                strSplited2 = strSplited[1].split("\\,");
                
                float prof[] = new float[strSplited2.length-1];
                
                float max = 0;
                float val = 0.0f;
                for (int j = 1; j < strSplited2.length; j++ ){
	                 val = Float.parseFloat(strSplited2[j]);
	                 prof[j-1] = val;
	                 if (val > max)
	                	 max = val;
                }
                
                for (int j = 1; j < strSplited2.length; j++ )
	                 prof[j-1] =  prof[j-1] / max;

                jTable1.setValueAt(prof, i, 7);
	                           
                jTable1.setValueAt(0.0f, i, 8); 

                i++;
                count++;
            } while (msgLine < str.length);
            
            for (int j = 0; j<count; j++ ){
                strSplited = str[msgLine].split("\\=");
                strSplited2 = strSplited[1].split("\\:");
            	jTable1.setValueAt(Float.parseFloat(strSplited2[0]), i-count+j, 8);
                jTable1.setValueAt(Integer.parseInt(strSplited2[1]), i-count+j, 9);
                //jTable1.setValueAt(0.58f, i-count+j, 8);
                //jTable1.setValueAt(1, i-count+j, 9);

                msgLine++;
            }
                          
        } while (msgLine < str.length);        
    }

    // Handler for timer
    public void startUTimer() {
        ActionListener action = new ActionListener() {
            public void actionPerformed(@SuppressWarnings("unused")
                        java.awt.event.ActionEvent e) {
                            if (jcboxUpdate.isSelected())
                                ProfileMonitorView.this.fillComponents();
                    }
        };
        Timer t = new Timer(1000, action);
        t.start();
    }

    @Action
    public void showSettings() {
        if (jdiaSettings == null) {
            JFrame mainFrame = ProfileMonitorApp.getApplication().getMainFrame();
            jdiaSettings = new JDialog(mainFrame);
            jdiaSettings.setLocationRelativeTo(mainFrame);
        }
        ProfileMonitorApp.getApplication().show(jdiaSettings);
    }

    @Action
    public void closeSettings() {
        jdiaSettings.dispose();
    }

    @Action
    public void settingsSetValues() {
        strIP = jtextIP.getText();
        strPort = jtextPort.getText();
        jdiaSettings.dispose();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable jTable1;
    private javax.swing.JTable jTable2;
    private javax.swing.JButton jbutCancel;
    private javax.swing.JButton jbutOk;
    private javax.swing.JButton jbutUpdate;
    private javax.swing.JCheckBox jcboxUpdate;
    private javax.swing.JDialog jdiaSettings;
    private javax.swing.JTextField jtextChannel;
    private javax.swing.JTextField jtextGSGenre;
    private javax.swing.JTextField jtextGuess;
    private javax.swing.JTextField jtextIP;
    private javax.swing.JTextField jtextLogged;
    private javax.swing.JTextField jtextPort;
    private javax.swing.JTextField jtextService;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JLabel statusAnimationLabel;
    private javax.swing.JLabel statusMessageLabel;
    private javax.swing.JPanel statusPanel;
    // End of variables declaration//GEN-END:variables

    private final Timer messageTimer;
    private final Timer busyIconTimer;
    private final Icon idleIcon;
    private final Icon[] busyIcons = new Icon[15];
    private int busyIconIndex = 0;

    private String strIP;
    private String strPort;

    private JDialog aboutBox;
}
