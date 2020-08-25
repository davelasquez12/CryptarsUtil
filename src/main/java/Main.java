import java.util.Map;

public class Main {
    public static void main(String[] args) {
        Map<String,String> iconUrlMap = GetIconsService.executeService();

        System.out.println(iconUrlMap);
    }
}
