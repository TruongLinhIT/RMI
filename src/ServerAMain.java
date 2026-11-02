import java.net.DatagramSocket;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class ServerAMain {
    public static final String SERVER_NAME = "Server A";

    // ==========================================================
    // CẤU HÌNH MẠNG CHO 2 MÁY (LAN) HOẶC 1 MÁY (LOCALHOST)
    // ==========================================================
    // - MY_IP: để "AUTO" sẽ tự lấy IP mạng LAN của máy này, hoặc điền trực tiếp "192.168.x.x".
    //   Nếu muốn test trên cùng 1 máy thì để "localhost".
    public static final String MY_IP = "AUTO"; 

    // - PEER_IP: IP của máy đang chạy Server B.
    //   Nếu test cùng 1 máy thì để "localhost", nếu chạy 2 máy khác nhau thì điền IP máy B vào đây.
    public static final String PEER_IP = "localhost"; 

    public static final int RMI_PORT = 1099;          // Port RMI của Server A
    public static final int PEER_PORT = 1100;         // Port RMI của Server B

    // Cấu hình CSDL Docker MySQL cục bộ của máy này
    public static final String DB_HOST = "localhost";
    public static final int DB_PORT = 3307;
    public static final String DB_NAME = "bank_a";     // CSDL riêng của Server A
    public static final String DB_USER = "root";
    public static final String DB_PASS = "123456";

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
            db.resetAllLocks();
            System.out.println("✅ Đã kết nối CSDL: " + DB_NAME);

            // 2. Xác định IP của máy hiện tại
            String actualMyIp = MY_IP.equalsIgnoreCase("AUTO") ? detectLocalIp() : MY_IP;
            System.setProperty("java.rmi.server.hostname", actualMyIp);

            // 3. Khởi tạo RMI Registry
            try {
                LocateRegistry.createRegistry(RMI_PORT);
            } catch (Exception e) {
                // Đã có registry mở trước đó
            }

            // 4. Đăng ký dịch vụ
            String myRmiUrl = "rmi://" + actualMyIp + ":" + RMI_PORT + "/BankService";
            String peerRmiUrl = "rmi://" + PEER_IP + ":" + PEER_PORT + "/BankService";

            IBankService service = new BankServerImpl(SERVER_NAME, db, peerRmiUrl);
            Naming.rebind(myRmiUrl, service);

            System.out.println("🚀 " + SERVER_NAME + " đang lắng nghe tại: " + myRmiUrl);
            System.out.println("🔄 Sẵn sàng đồng bộ 2 chiều với Server B (" + peerRmiUrl + ")");
            System.out.println("💡 (Nếu chạy 2 máy, hãy đảm bảo máy kia có thể ping thấy IP: " + actualMyIp + ")");
            System.out.println("=================================================");

        } catch (Exception e) {
            System.err.println("❌ Lỗi khởi động " + SERVER_NAME + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Hàm tự động phát hiện IP IPv4 thực tế của máy trong mạng LAN/Wifi
    public static String detectLocalIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return "localhost";
            }
        }
    }
}
