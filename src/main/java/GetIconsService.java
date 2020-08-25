import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GetIconsService {
    private static GetIconsAPI service = null;

    private static void init() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://rest.coinapi.io/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        service = retrofit.create(GetIconsAPI.class);
    }

    public static Map<String, String> executeService() {
        init();

        try {
            Response<ResponseBody> response = service.getCryptoIcons().execute();
            if(response.isSuccessful() && response.body() != null) {
                return mapResponse(response.body().string());
            } else {
                System.out.println("Error making service call");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Map<String, String> mapResponse(String jsonResponse) {
        JsonElement root = JsonParser.parseString(jsonResponse);
        JsonArray array = root.getAsJsonArray();
        Map<String, String> iconUrlMap = new HashMap<>(array.size() * 2 + 1);

        for(int i = 0; i < array.size(); i++) {
            JsonObject obj = array.get(i).getAsJsonObject();
            String key = obj.get("asset_id").getAsString();
            String url = obj.get("url").getAsString();
            iconUrlMap.put(key, url);
        }
        return iconUrlMap;
    }
}