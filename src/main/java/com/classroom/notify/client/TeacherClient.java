package com.classroom.notify.client;


import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * 教师端程序：启动时自动扫描局域网内的班级端，
 * 连接成功后显示主窗口，通过快捷/自定义标签页发送通知。
 */
public class TeacherClient {

    private static final int PORT = 12345;
    private Socket socket;
    private PrintWriter writer;
    private volatile boolean connected = false;

    // 主窗口组件
    private JFrame mainFrame;
    private JTextField nameField;
    private JComboBox<String> officeCombo;
    private JTextField noteField;
    private JTextArea customArea;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TeacherClient().start());
    }

    private void start() {
        // 弹出连接等待对话框
        JDialog connectingDialog = createConnectingDialog();
        // 后台扫描局域网并连接
        // Use a regular thread for compatibility
        Thread starter = new Thread(() -> {
            String ip = scanLan();
            if (ip == null) {
                SwingUtilities.invokeLater(() -> {
                    connectingDialog.dispose();
                    JOptionPane.showMessageDialog(null, "未在局域网内找到班级端，请确认班级端已启动。",
                            "连接失败", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                });
                return;
            }
            try {
                socket = new Socket(ip, PORT);
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                connected = true;
                SwingUtilities.invokeLater(() -> {
                    connectingDialog.dispose();
                    buildMainWindow();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    connectingDialog.dispose();
                    JOptionPane.showMessageDialog(null, "连接班级端失败: " + e.getMessage(),
                            "错误", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                });
            }
        });
        starter.start();
        connectingDialog.setVisible(true); // 模态阻塞，直到 dispose
    }

    /**
     * 创建“正在连接班级”模态对话框。
     */
    private JDialog createConnectingDialog() {
        JDialog dialog = new JDialog();
        dialog.setTitle("正在连接班级");
        dialog.setModal(true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                System.exit(0); // 用户强行关闭窗口则退出程序
            }
        });
        JLabel label = new JLabel("正在搜索并连接班级端，请稍候…", SwingConstants.CENTER);
        label.setFont(new Font("宋体", Font.PLAIN, 16));
        label.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        dialog.add(label);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        return dialog;
    }

    /**
     * 扫描局域网，返回第一个可达班级端的 IP，超时约 8 秒。
     */
    private String scanLan() {
        List<String> localIPs = getLocalSiteIPv4();
        if (localIPs.isEmpty()) return null;

        BlockingQueue<String> resultQueue = new LinkedBlockingQueue<>();
        List<Thread> workers = new ArrayList<>();

        for (String ip : localIPs) {
            String prefix = ip.substring(0, ip.lastIndexOf('.') + 1);
            for (int i = 1; i <= 254; i++) {
                String target = prefix + i;
                Thread t = new Thread(() -> {
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(target, PORT), 200);
                        resultQueue.put(target);
                    } catch (Exception ignored) {}
                });
                t.start();
                workers.add(t);
            }
        }
        try {
            // 等待任一成功，最多 8 秒
            String found = resultQueue.poll(8, TimeUnit.SECONDS);
            // 中断剩余线程
            for (Thread t : workers) t.interrupt();
            return found;
        } catch (InterruptedException e) {
            return null;
        }
    }

    /**
     * 获取本机所有 site-local IPv4 地址。
     */
    private List<String> getLocalSiteIPv4() {
        List<String> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        list.add(addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {}
        return list;
    }

    // ===================== 主窗口构建 =====================
    private void buildMainWindow() {
        mainFrame = new JFrame("教师端 - 发送通知");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("快捷发送", createQuickPanel());
        tabbedPane.addTab("自定义发送", createCustomPanel());
        mainFrame.add(tabbedPane, BorderLayout.CENTER);

        // 底部关于按钮
        JButton aboutBtn = new JButton("关于");
        aboutBtn.addActionListener(e -> showAbout());
        JPanel bottomPanel = new JPanel();
        bottomPanel.add(aboutBtn);
        mainFrame.add(bottomPanel, BorderLayout.SOUTH);

        mainFrame.setSize(620, 400);
        mainFrame.setMinimumSize(new Dimension(620, 400));
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }

    /**
     * 快捷发送标签页。
     */
    private JPanel createQuickPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        // 第一行：请 [姓名] 到 [办公室] 办公室报到！
        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        panel.add(new JLabel("请"), g);

        g.gridx = 1; g.weightx = 1;
        nameField = new JTextField(8);
        nameField.setFont(new Font("宋体", Font.PLAIN, 18));
        panel.add(nameField, g);

        g.gridx = 2; g.weightx = 0;
        panel.add(new JLabel("到"), g);

        g.gridx = 3; g.weightx = 1;
        officeCombo = new JComboBox<>(new String[]{"班主任", "语文", "数学", "英语", "自定义输入"});
        officeCombo.setEditable(true);
        officeCombo.setFont(new Font("宋体", Font.PLAIN, 18));
        // 选择“自定义输入”时清空编辑器，方便用户直接输入
        officeCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && "自定义输入".equals(e.getItem())) {
                officeCombo.setSelectedItem("");
                officeCombo.getEditor().selectAll();
            }
        });
        panel.add(officeCombo, g);

        g.gridx = 4; g.weightx = 0;
        panel.add(new JLabel("办公室报到！"), g);

        // 第二行：备注
        g.gridx = 0; g.gridy = 1; g.weightx = 0;
        panel.add(new JLabel("备注："), g);
        g.gridx = 1; g.gridwidth = 4; g.weightx = 1;
        noteField = new JTextField(20);
        noteField.setFont(new Font("宋体", Font.PLAIN, 18));
        panel.add(noteField, g);

        // 第三行：发送按钮
        g.gridx = 0; g.gridy = 2; g.gridwidth = 5;
        g.anchor = GridBagConstraints.CENTER; g.fill = GridBagConstraints.NONE;
        JButton sendBtn = new JButton("发送");
        sendBtn.setFont(new Font("宋体", Font.BOLD, 16));
        sendBtn.addActionListener(e -> sendQuick());
        panel.add(sendBtn, g);

        return panel;
    }

    /**
     * 自定义发送标签页。
     */
    private JPanel createCustomPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panel.add(new JLabel("请输入需要发送的自定义消息："), BorderLayout.NORTH);

        customArea = new JTextArea(6, 30);
        customArea.setFont(new Font("宋体", Font.PLAIN, 18));
        customArea.setLineWrap(true);
        customArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(customArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        JButton sendBtn = new JButton("发送");
        sendBtn.setFont(new Font("宋体", Font.BOLD, 16));
        sendBtn.addActionListener(e -> sendCustom());
        JPanel btnPanel = new JPanel();
        btnPanel.add(sendBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ===================== 发送逻辑 =====================
    private void sendQuick() {
        if (!connected) {
            JOptionPane.showMessageDialog(mainFrame, "未连接到班级端", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "请输入姓名", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String office = officeCombo.getEditor().getItem().toString().trim();
        if (office.isEmpty() || "自定义输入".equals(office)) {
            JOptionPane.showMessageDialog(mainFrame, "请选择或输入办公室类型", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String note = noteField.getText().trim();

        try {
            writer.println("QUICK");
            writer.println(name);
            writer.println(office);
            writer.println(note);
            writer.println("---END---");
            writer.flush();
            JOptionPane.showMessageDialog(mainFrame, "消息已发送", "提示", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "发送失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendCustom() {
        if (!connected) {
            JOptionPane.showMessageDialog(mainFrame, "未连接到班级端", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String msg = customArea.getText().trim();
        if (msg.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "请输入自定义消息", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            writer.println("CUSTOM");
            writer.println(msg);
            writer.println("---END---");
            writer.flush();
            JOptionPane.showMessageDialog(mainFrame, "消息已发送", "提示", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "发送失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===================== 关于 =====================
    private void showAbout() {
        String content;
        try {
            Path path = Paths.get("about.txt");
            content = Files.exists(path) ? Files.readString(path) : "about.txt 文件未找到。";
        } catch (IOException e) {
            content = "读取 about.txt 失败: " + e.getMessage();
        }
        JTextArea area = new JTextArea(content);
        area.setEditable(false);
        area.setFont(new Font("宋体", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setPreferredSize(new Dimension(450, 300));
        JOptionPane.showMessageDialog(mainFrame, scrollPane, "关于", JOptionPane.INFORMATION_MESSAGE);
    }
}