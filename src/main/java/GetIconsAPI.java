import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;

public interface GetIconsAPI {
    @Headers("X-CoinAPI-Key: " + ApiKeys.COIN_API_KEY)
    @GET("v1/assets/icons/50")
    Call<ResponseBody> getCryptoIcons();
}
