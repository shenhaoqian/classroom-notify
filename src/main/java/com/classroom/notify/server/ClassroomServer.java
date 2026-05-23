package com.classroom.notify.server;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;

/**
 * 班级端：最终修复版 - 解决 invokeLater 不执行的问题
 */
public class ClassroomServer {

    private static final int PORT = 12345;
    private static boolean isSwingInitialized = false;

    public static void main(String[] args) {
        System.out.println("=== 班级端启动 ===");
        System.out.println("Java 版本: " + System.getProperty("java.version"));
        System.out.println("操作系统: " + System.getProperty("os.name"));
        
        // 强制初始化 Swing 工具包
        System.out.println("初始化 Swing 环境...");
        try {
            SwingUtilities.invokeAndWait(() -> {
                // 创建并立即销毁一个隐藏窗口以初始化 EDT
                JFrame initFrame = new JFrame();
                initFrame.setUndecorated(true);
                initFrame.setSize(0, 0);
                initFrame.setVisible(true);
                initFrame.dispose();
                isSwingInitialized = true;
                System.out.println("Swing 环境初始化成功");
            });
        } catch (Exception e) {
            System.err.println("Swing 初始化失败: " + e);
            e.printStackTrace();
            isSwingInitialized = false;
        }
        
        System.out.println("启动服务器...");
        startServer();
    }

    private static void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("班级端已启动，监听端口 " + PORT + " ...");
            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("教师端已连接: " + client.getInetAddress());
                Thread.startVirtualThread(() -> handleClient(client));
            }
        } catch (IOException e) {
            System.err.println("服务器异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"))) {
            String type;
            while ((type = reader.readLine()) != null) {
                System.out.println("收到指令行: " + type);
                switch (type) {
                    case "QUICK" -> {
                        String name = reader.readLine();
                        String office = reader.readLine();
                        String note = reader.readLine();
                        String endMark = reader.readLine();
                        System.out.printf("QUICK 解析: name='%s', office='%s', note='%s', endMark='%s'%n",
                                name, office, note, endMark);
                        
                        // 直接在当前线程显示弹窗（不依赖 invokeLater）
                        System.out.println("准备显示快捷消息弹窗...");
                        showQuickDialogDirect(name, office, note);
                    }
                    case "CUSTOM" -> {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while (!"---END---".equals(line = reader.readLine())) {
                            sb.append(line).append('\n');
                        }
                        String msg = sb.toString().trim();
                        System.out.println("CUSTOM 消息内容: " + msg);
                        System.out.println("准备显示自定义消息弹窗...");
                        showCustomDialogDirect(msg);
                    }
                    default -> System.err.println("未知指令: " + type);
                }
            }
        } catch (IOException e) {
            System.err.println("与教师端通信错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * 直接在当前线程显示快捷消息弹窗
     */
    private static void showQuickDialogDirect(String name, String office, String note) {
        try {
            System.out.println("开始构建快捷消息弹窗");
            playSound();

            // 确保在 EDT 中创建和显示，但如果 invokeLater 不工作，则回退到当前线程
            if (EventQueue.isDispatchThread()) {
                System.out.println("当前已在 EDT 中，直接创建弹窗");
                createAndShowQuickDialog(name, office, note);
            } else {
                System.out.println("不在 EDT 中，尝试使用 invokeAndWait");
                try {
                    SwingUtilities.invokeAndWait(() -> createAndShowQuickDialog(name, office, note));
                } catch (Exception e) {
                    System.err.println("invokeAndWait 失败，回退到当前线程创建: " + e);
                    createAndShowQuickDialog(name, office, note);
                }
            }
            System.out.println("快捷消息弹窗创建完成");
        } catch (Exception e) {
            System.err.println("快捷弹窗异常: " + e);
            e.printStackTrace();
            // 终极回退：使用 JOptionPane 在当前线程
            try {
                JOptionPane.showMessageDialog(null, "请 " + name + " 到 " + office + " 办公室\n备注：" + note, 
                        "通知", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                System.err.println("JOptionPane 也失败: " + ex);
            }
        }
    }

    /**
     * 直接在当前线程显示自定义消息弹窗
     */
    private static void showCustomDialogDirect(String message) {
        try {
            System.out.println("开始构建自定义消息弹窗");
            playSound();

            if (EventQueue.isDispatchThread()) {
                System.out.println("当前已在 EDT 中，直接创建弹窗");
                createAndShowCustomDialog(message);
            } else {
                System.out.println("不在 EDT 中，尝试使用 invokeAndWait");
                try {
                    SwingUtilities.invokeAndWait(() -> createAndShowCustomDialog(message));
                } catch (Exception e) {
                    System.err.println("invokeAndWait 失败，回退到当前线程创建: " + e);
                    createAndShowCustomDialog(message);
                }
            }
            System.out.println("自定义消息弹窗创建完成");
        } catch (Exception e) {
            System.err.println("自定义弹窗异常: " + e);
            e.printStackTrace();
            try {
                JOptionPane.showMessageDialog(null, message, "教师消息", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                System.err.println("JOptionPane 也失败: " + ex);
            }
        }
    }

    /**
     * 创建并显示快捷消息弹窗（必须在 EDT 中调用）
     */
    private static void createAndShowQuickDialog(String name, String office, String note) {
        System.out.println("createAndShowQuickDialog 被调用");
        
        JDialog dialog = new JDialog();
        dialog.setTitle("通知");
        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setAlwaysOnTop(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));

        // 主提示行
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JLabel prefix = new JLabel("请");
        prefix.setFont(new Font("微软雅黑", Font.PLAIN, 28));
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("微软雅黑", Font.BOLD, 56));
        
        JLabel mid = new JLabel("到");
        mid.setFont(new Font("微软雅黑", Font.PLAIN, 28));
        JLabel officeLabel = new JLabel(office + "办公室");
        officeLabel.setFont(new Font("微软雅黑", Font.BOLD, 36));
        JLabel suffix = new JLabel("报道！");
        suffix.setFont(new Font("微软雅黑", Font.PLAIN, 28));
        topPanel.add(prefix);
        topPanel.add(nameLabel);
        topPanel.add(mid);
        topPanel.add(officeLabel);
        topPanel.add(suffix);
        dialog.add(topPanel, BorderLayout.CENTER);

        // 备注
        if (note != null && !note.isEmpty()) {
            JPanel notePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JLabel noteTitle = new JLabel("备注：");
            noteTitle.setFont(new Font("微软雅黑", Font.PLAIN, 18));
            JLabel noteContent = new JLabel(note);
            noteContent.setFont(new Font("微软雅黑", Font.PLAIN, 18));
            notePanel.add(noteTitle);
            notePanel.add(noteContent);
            dialog.add(notePanel, BorderLayout.NORTH);
        }

        // 确认按钮 + 倒计时
        JPanel btnPanel = new JPanel();
        JButton confirmBtn = new JButton();
        btnPanel.add(confirmBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        
        // 设置倒计时
        final int[] remaining = {30};
        confirmBtn.setText("确认(" + remaining[0] + ")");
        Timer timer = new Timer(1000, e -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                dialog.dispose();
            } else {
                confirmBtn.setText("确认(" + remaining[0] + ")");
            }
        });
        timer.start();
        confirmBtn.addActionListener(e -> dialog.dispose());
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { timer.stop(); }
            @Override public void windowClosing(WindowEvent e) { timer.stop(); }
        });
        
        dialog.setVisible(true);
        System.out.println("快捷消息弹窗已显示并等待关闭");
    }

    /**
     * 创建并显示自定义消息弹窗（必须在 EDT 中调用）
     */
    private static void createAndShowCustomDialog(String message) {
        System.out.println("createAndShowCustomDialog 被调用");
        
        JDialog dialog = new JDialog();
        dialog.setTitle("教师消息");
        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setAlwaysOnTop(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));

        JLabel header = new JLabel("收到教师消息：", SwingConstants.CENTER);
        header.setFont(new Font("微软雅黑", Font.BOLD, 24));
        dialog.add(header, BorderLayout.NORTH);

        JTextArea textArea = new JTextArea(message);
        textArea.setFont(new Font("微软雅黑", Font.PLAIN, 20));
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(480, 250));
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        JButton confirmBtn = new JButton("确认(30)");
        final int[] remaining = {30};
        confirmBtn.setText("确认(" + remaining[0] + ")");
        confirmBtn.addActionListener(e -> dialog.dispose());
        
        Timer timer = new Timer(1000, e -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                dialog.dispose();
            } else {
                confirmBtn.setText("确认(" + remaining[0] + ")");
            }
        });
        timer.start();
        
        btnPanel.add(confirmBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { timer.stop(); }
            @Override public void windowClosing(WindowEvent e) { timer.stop(); }
        });

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        System.out.println("自定义消息弹窗已显示并等待关闭");
    }

    /**
     * 播放 msg.wav
     */
    private static void playSound() {
        Thread.startVirtualThread(() -> {
            try {
                Path soundFile = Paths.get("msg.wav");
                if (Files.exists(soundFile)) {
                    System.out.println("播放提示音: msg.wav");
                    AudioInputStream stream = AudioSystem.getAudioInputStream(soundFile.toFile());
                    Clip clip = AudioSystem.getClip();
                    clip.open(stream);
                    clip.start();
                    Thread.sleep(clip.getMicrosecondLength() / 1000);
                    clip.close();
                    System.out.println("提示音播放结束");
                } else {
                    System.err.println("msg.wav 未找到，跳过声音提示");
                }
            } catch (Exception ex) {
                System.err.println("播放声音失败: " + ex.getMessage());
            }
        });
    }
}