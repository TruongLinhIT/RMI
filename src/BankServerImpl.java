import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class BankServerImpl extends UnicastRemoteObject implements IBankService {
    private DBManager db;
    private String peerRmiUrl;
    private String serverName;

    public BankServerImpl(String serverName, DBManager db, String peerRmiUrl) throws RemoteException {
        super();
        this.serverName = serverName;
        this.db = db;
        this.peerRmiUrl = peerRmiUrl;
    }

    private IBankService getPeerServer() {
        if (peerRmiUrl == null || peerRmiUrl.trim().isEmpty()) {
            return null;
        }
        try {
            return (IBankService) Naming.lookup(peerRmiUrl);
        } catch (Exception e) {
            System.out.println("[" + serverName + "] ⚠️  Chưa kết nối được Server đối tác (" + peerRmiUrl + ")");
            return null;
        }
    }

    @Override
    public synchronized String login(String username, String password) throws RemoteException {
        Account acc = db.getAccount(username);

        if (acc == null || !acc.getPassword().equals(password)) {
            return "SAI_THONG_TIN";
        }

        // Kiểm tra xem tài khoản có đang đăng nhập ở Server này hoặc Server kia không
        if (acc.isLocked()) {
            return "DANG_DANG_NHAP_NOI_KHAC";
        }

        // 1. Khóa tài khoản trong CSDL của Server này
        db.updateLockStatus(username, true);
        System.out.println("[" + serverName + "] 👤 User '" + username + "' đã đăng nhập -> Đã khóa trên CSDL cục bộ.");

        // 2. Đồng bộ khóa tài khoản sang CSDL của Server đối tác qua RMI
        IBankService peer = getPeerServer();
        if (peer != null) {
            try {
                peer.syncLockStatus(username, true);
            } catch (Exception e) {
                System.out.println("[" + serverName + "] ⚠️  Lỗi đồng bộ lock sang đối tác: " + e.getMessage());
            }
        }

        return "OK";
    }

    @Override
    public synchronized void logout(String username) throws RemoteException {
        // 1. Mở khóa trên CSDL cục bộ
        db.updateLockStatus(username, false);
        System.out.println("[" + serverName + "] 🔓 User '" + username + "' đã đăng xuất -> Đã mở khóa CSDL.");

        // 2. Đồng bộ mở khóa sang CSDL của Server đối tác
        IBankService peer = getPeerServer();
        if (peer != null) {
            try {
                peer.syncLockStatus(username, false);
            } catch (Exception e) {
                System.out.println("[" + serverName + "] ⚠️  Lỗi đồng bộ unlock sang đối tác: " + e.getMessage());
            }
        }
    }

    @Override
    public synchronized double getBalance(String username) throws RemoteException {
        Account acc = db.getAccount(username);
        return (acc != null) ? acc.getBalance() : 0;
    }

    @Override
    public synchronized String transfer(String fromUser, String toUser, double amount) throws RemoteException {
        if (amount <= 0) {
            return "Số tiền chuyển phải lớn hơn 0!";
        }
        if (fromUser.equalsIgnoreCase(toUser)) {
            return "Không thể tự chuyển tiền cho chính mình!";
        }

        Account fromAcc = db.getAccount(fromUser);
        Account toAcc = db.getAccount(toUser);

        if (fromAcc == null) {
            return "Tài khoản người gửi không tồn tại!";
        }
        if (toAcc == null) {
            return "Tài khoản người nhận không tồn tại!";
        }
        if (fromAcc.getBalance() < amount) {
            return "Số dư không đủ để thực hiện giao dịch!";
        }

        double newFromBalance = fromAcc.getBalance() - amount;
        double newToBalance = toAcc.getBalance() + amount;

        // 1. Cập nhật số dư vào CSDL của Server này (dùng Transaction an toàn)
        boolean ok = db.updateTransfer(fromUser, newFromBalance, toUser, newToBalance);
        if (!ok) {
            return "Lỗi CSDL khi thực hiện giao dịch!";
        }

        System.out.printf("[%s] 💸 Giao dịch thành công: %s -> %s (%,.0f VNĐ)\n", serverName, fromUser, toUser, amount);

        // 2. Đồng bộ số dư mới sang CSDL của Server đối tác qua RMI
        IBankService peer = getPeerServer();
        if (peer != null) {
            try {
                peer.syncBalance(fromUser, newFromBalance, toUser, newToBalance);
            } catch (Exception e) {
                System.out.println("[" + serverName + "] ⚠️  Lỗi đồng bộ số dư sang đối tác: " + e.getMessage());
            }
        }

        return "OK";
    }

    // =========================================================================
    // CÁC HÀM NHẬN LỆNH ĐỒNG BỘ TỪ SERVER ĐỐI TÁC
    // =========================================================================

    @Override
    public synchronized void syncLockStatus(String username, boolean isLocked) throws RemoteException {
        db.updateLockStatus(username, isLocked);
        System.out.println("[" + serverName + " - 🔄 ĐỒNG BỘ TỪ SERVER KIA] Đã cập nhật is_locked = " + isLocked + " cho user '" + username + "'");
    }

    @Override
    public synchronized void syncBalance(String fromUser, double newFromBalance, String toUser, double newToBalance) throws RemoteException {
        db.updateTransfer(fromUser, newFromBalance, toUser, newToBalance);
        System.out.printf("[%s - 🔄 ĐỒNG BỘ TỪ SERVER KIA] Đã đồng bộ số dư: %s = %,.0f VNĐ, %s = %,.0f VNĐ\n", 
                serverName, fromUser, newFromBalance, toUser, newToBalance);
    }
}
