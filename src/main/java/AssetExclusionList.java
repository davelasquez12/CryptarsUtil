import java.util.HashSet;

public class AssetExclusionList {
    private static final HashSet<String> exclusionSet = new HashSet<>();
    static {
        exclusionSet.add("XRP");
        exclusionSet.add("XLM");
    }

    public static boolean contains(String asset) {
        return asset != null && !asset.isEmpty() && exclusionSet.contains(asset);
    }
}
