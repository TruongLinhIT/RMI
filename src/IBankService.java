import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IBankService extends Remote {
    // --- CÁC HÀM PHỤC VỤ CLIENT ---
    String login(String username, String password) throws RemoteException;
    void logout(String username) throws RemoteException;
    double getBalance(String username) throws RemoteException;
    String transfer(String fromUser, String toUser, double amount) throws RemoteException;

    // --- CÁC HÀM ĐỒNG BỘ 2 CHIỀU GIỮA SERVER A VÀ SERVER B ---
    void syncLockStatus(String username, boolean isLocked) throws RemoteException;
    void syncBalance(String fromUser, double newFromBalance, String toUser, double newToBalance) throws RemoteException;
}
