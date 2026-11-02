import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class ServerMain {
    // === CẤU HÌNH DATABASE DOCKER ===
    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 3307;
    private static final String DB_NAME = "rmi_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "123456";

    // === CẤU HÌNH RMI SERVER ===
    private static final String SERVER_NAME = "BankServer";
    private static final int RMI_PORT = 1099;

    // Nếu có Server thứ 2 để đồng bộ thì điền URL vào đây, không thì để trống ""
    private static final String PEER_RMI_URL = ""; 

    public static void main(String[] args) {
        try {
            System.out.println("==========================================");
            System.out.println("⏳ Đang khởi động " + SERVER_NAME + " và kết nối MySQL...");

            // 1. Kết nối thẳng vào MySQL Docker
            DBManager db = new DBManager(DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS);
            if (!db.testConnection()) {
                System.err.println("❌ Không thể kết nối Database!");
                System.err.println("👉 Hãy đảm bảo Docker đang chạy: docker compose up -d");
                return;
            }
            System.out.println("✅ Đã kết nối Database MySQL (" + DB_NAME + ") thành công!");

            // 2. Khởi tạo RMI Registry
            System.setProperty("java.rmi.server.hostname", "localhost");
            try {
                LocateRegistry.createRegistry(RMI_PORT);
            } catch (Exception e) {
                // Registry đã mở từ trước thì bỏ qua lỗi
            }

            // 3. Đăng ký dịch vụ RMI
            String rmiUrl = "rmi://localhost:" + RMI_PORT + "/BankService";
            IBankService service = new BankServerImpl(SERVER_NAME, db, PEER_RMI_URL);
            Naming.rebind(rmiUrl, service);

            System.out.println("🚀 Server đã sẵn sàng phục vụ tại: " + rmiUrl);
            System.out.println("==========================================");

        } catch (Exception e) {
            System.err.println("❌ Lỗi khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
