import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class ServerAMain {
    // === CẤU HÌNH SERVER A ===
    public static final String SERVER_NAME = "Server A";
    public static final String DB_HOST = "localhost";
    public static final int DB_PORT = 3307;
    public static final String DB_NAME = "bank_a";     // CSDL riêng của Server A
    public static final String DB_USER = "root";
    public static final String DB_PASS = "123456";

    public static final int RMI_PORT = 1099;          // Port RMI của Server A
    public static final String PEER_RMI_URL = "rmi://localhost:1100/BankService"; // Server B

    public static void main(String[] args) {
        try {
            System.out.println("=================================================");
            System.out.println("⏳ Đang khởi động " + SERVER_NAME + "...");

            // 1. Kết nối CSDL Server A
            DBManager db = new DBManager(DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS);
            if (!db.testConnection()) {
                System.err.println("❌ Không thể kết nối tới CSDL " + DB_NAME + "!");
                System.err.println("👉 Vui lòng chạy Docker: docker compose up -d");
                return;
            }
            // Mở khóa các tài khoản nếu lần chạy trước chưa kịp logout
            db.resetAllLocks();
            System.out.println("✅ Đã kết nối CSDL: " + DB_NAME);

            // 2. Khởi tạo RMI Registry cho Server A
            System.setProperty("java.rmi.server.hostname", "localhost");
            try {
                LocateRegistry.createRegistry(RMI_PORT);
            } catch (Exception e) {
                // Đã có registry mở trước đó
            }

            // 3. Đăng ký dịch vụ
            String myRmiUrl = "rmi://localhost:" + RMI_PORT + "/BankService";
            IBankService service = new BankServerImpl(SERVER_NAME, db, PEER_RMI_URL);
            Naming.rebind(myRmiUrl, service);

            System.out.println("🚀 " + SERVER_NAME + " đang lắng nghe tại: " + myRmiUrl);
            System.out.println("🔄 Sẵn sàng đồng bộ 2 chiều với Server B (" + PEER_RMI_URL + ")");
            System.out.println("=================================================");

        } catch (Exception e) {
            System.err.println("❌ Lỗi khởi động " + SERVER_NAME + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}

