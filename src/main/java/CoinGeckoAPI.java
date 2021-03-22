import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

public interface CoinGeckoAPI {
    @GET("https://api.coingecko.com/api/v3/coins/list")
    Call<ResponseBody> getCryptoIdsAndNames();
}
