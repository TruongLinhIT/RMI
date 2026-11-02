import java.sql.*;

public class DBManager {
    private String host;
    private int port;
    private String dbName;
    private String dbUser;
    private String dbPass;
    private String dbUrl;

    public DBManager(String dbName, String dbUser, String dbPass) {
        this("localhost", 3307, dbName, dbUser, dbPass);
    }

    public DBManager(String host, int port, String dbName, String dbUser, String dbPass) {
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPass = dbPass;
        this.dbUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }

    // Kiểm tra kết nối CSDL và TỰ ĐỘNG TẠO DATABASE / BẢNG nếu chưa có
    public boolean testConnection() {
        // 1. Kiểm tra xem có tạo được kết nối cơ sở không
        String serverUrl = "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection serverConn = DriverManager.getConnection(serverUrl, dbUser, dbPass);
             Statement stmt = serverConn.createStatement()) {
            
            // Tự động tạo Database nếu chưa tồn tại
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName + ";");
            
        } catch (SQLException e) {
            System.err.println("❌ Không thể kết nối tới MySQL Server tại " + host + ":" + port + " (" + e.getMessage() + ")");
            return false;
        }

        // 2. Kết nối trực tiếp vào Database vừa tạo và đảm bảo có bảng accounts cùng dữ liệu mẫu
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Tạo bảng accounts nếu chưa có
            String createTableSql = "CREATE TABLE IF NOT EXISTS accounts ("
                    + "username VARCHAR(50) PRIMARY KEY, "
                    + "password VARCHAR(255) NOT NULL, "
                    + "balance DOUBLE NOT NULL DEFAULT 0, "
                    + "is_locked BOOLEAN NOT NULL DEFAULT FALSE"
                    + ");";
            stmt.executeUpdate(createTableSql);

            // Nạp dữ liệu mẫu ban đầu nếu chưa có
            String insertSampleSql = "INSERT INTO accounts (username, password, balance, is_locked) VALUES "
                    + "('user1', '123', 5000000.0, FALSE), "
                    + "('user2', '123', 3000000.0, FALSE), "
                    + "('user3', '123', 10000000.0, FALSE) "
                    + "ON DUPLICATE KEY UPDATE username = username;";
            stmt.executeUpdate(insertSampleSql);

            return true;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cấu trúc bảng CSDL (" + dbUrl + "): " + e.getMessage());
            return false;
        }
    }

    // Reset lại toàn bộ lock về false khi Server khởi động (tránh kẹt lock do tắt ngang ứng dụng)
    public void resetAllLocks() {
        String sql = "UPDATE accounts SET is_locked = FALSE";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 1. Lấy thông tin tài khoản
    public Account getAccount(String username) {
        String sql = "SELECT * FROM accounts WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Account(
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getDouble("balance"),
                        rs.getBoolean("is_locked")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 2. Cập nhật trạng thái khóa/mở khóa tài khoản (is_locked)
    public boolean updateLockStatus(String username, boolean isLocked) {
        String sql = "UPDATE accounts SET is_locked = ? WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setBoolean(1, isLocked);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 3. Cập nhật số dư 2 tài khoản trong 1 Transaction (giao dịch an toàn CSDL)
    public boolean updateTransfer(String fromUser, double newFromBalance, String toUser, double newToBalance) {
        String sql = "UPDATE accounts SET balance = ? WHERE username = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Bắt đầu Transaction
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                // Trừ tiền người gửi
                ps.setDouble(1, newFromBalance);
                ps.setString(2, fromUser);
                ps.executeUpdate();

                // Cộng tiền người nhận
                ps.setDouble(1, newToBalance);
                ps.setString(2, toUser);
                ps.executeUpdate();

                conn.commit(); // Hoàn tất Transaction
                return true;
            } catch (SQLException ex) {
                conn.rollback(); // Hoàn tác nếu có lỗi
                ex.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 4. Cập nhật số dư 1 tài khoản (dùng khi đồng bộ đơn lẻ nếu cần)
    public boolean updateBalance(String username, double newBalance) {
        String sql = "UPDATE accounts SET balance = ? WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newBalance);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
