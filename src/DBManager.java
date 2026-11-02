import java.sql.*;

public class DBManager {
    private String dbUrl;
    private String dbUser;
    private String dbPass;

    public DBManager(String dbName, String dbUser, String dbPass) {
        this("localhost", 3307, dbName, dbUser, dbPass);
    }

    public DBManager(String host, int port, String dbName, String dbUser, String dbPass) {
        this.dbUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        this.dbUser = dbUser;
        this.dbPass = dbPass;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }

    // Kiểm tra kết nối CSDL khi Server khởi động
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("❌ Lỗi kết nối CSDL (" + dbUrl + "): " + e.getMessage());
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
