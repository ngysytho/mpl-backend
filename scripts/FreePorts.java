import java.net.InetAddress;
import java.net.ServerSocket;
/** Reserve three distinct loopback ports while selecting fixture endpoints. Compose binds them next. */
class FreePorts {
    public static void main(String[] args) throws Exception {
        try (var api = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var iam = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var db = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            System.out.printf("%d %d %d%n", api.getLocalPort(), iam.getLocalPort(), db.getLocalPort());
        }
    }
}
