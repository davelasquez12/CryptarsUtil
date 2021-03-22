import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* Class used to retrieve asset names from CoinGecko API and store them to the TradingPairs table in DynamoDB
* */
public class AddAssetNames {
    public static final String TABLE_NAME = "TradingPairs";
    private static DynamoDbClient dynamoDbClient;

    public static void run() {
        initDynamoDbClient();
        Set<String> assetIdSet = getAssetIdSetFromDb();
        Map<String, String> assetIdNameMap = getAssetIdNameMap(assetIdSet);
        if(assetIdNameMap != null) {
            List<ErrorInfo> errorInfoList = saveNamesToDB(assetIdNameMap);

            if(errorInfoList.isEmpty()) {
                System.out.println("Added asset names to DB successfully!");
            }
            else {
                System.out.println("Errors found:");
                for(ErrorInfo errorInfo : errorInfoList) {
                    System.out.println(errorInfo.getMessage());
                }
            }
        }
        else {
            System.out.println("ERROR: assetIdNameMap is null");
        }
    }

    private static Map<String, String> getAssetIdNameMap(Set<String> assetIdSet){
        Map<String, String> assetIdNameMap = new HashMap<>(651);    //key = assetId, value = assetname (e.g. key = "ethereum", value = "Ethereum")
        CoinGeckoAPI coinGeckoAPI = initCoinGeckoApi();
        Response<ResponseBody> response = null;

        try {
            response = coinGeckoAPI.getCryptoIdsAndNames().execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        if(response.isSuccessful() && response.body() != null) {
            System.out.println("CoinGecko API called successfully!");
            JsonElement responseJsonElement = convertResponseToJsonElement(response.body());

            if(responseJsonElement != null) {
                assetIdNameMap = matchIdsToData(responseJsonElement, assetIdSet);
                if(assetIdNameMap.isEmpty()) {
                    System.out.println("ERROR: assetIdNameMap is empty");
                }
            }
        }

        return assetIdNameMap;
    }

    private static void initDynamoDbClient() {
        if(dynamoDbClient == null) {
            dynamoDbClient = DynamoDbClient.builder()
                    .region(Region.US_EAST_2)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        }
    }

    private static CoinGeckoAPI initCoinGeckoApi() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.coingecko.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(CoinGeckoAPI.class);
    }


    private static Set<String> getAssetIdSetFromDb() {
        ScanRequest scanRequest = ScanRequest.builder().tableName(TABLE_NAME).attributesToGet("id").build();
        ScanResponse response = dynamoDbClient.scan(scanRequest);

        if(response.sdkHttpResponse().isSuccessful() && response.count() > 0) {
            List<Map<String, AttributeValue>> assetIdItems = response.items();

            Set<String> assetIdSet = new HashSet<>(651);   //nice prime number to prevent collisions to hold 400 assets

            if(assetIdItems != null) {
                for(Map<String, AttributeValue> assetIdItem : assetIdItems) {
                    assetIdSet.add(assetIdItem.get("id").s());
                }
                return assetIdSet;
            }
        }

        return null;
    }

    private static JsonElement convertResponseToJsonElement(ResponseBody responseBody) {
        String jsonStr;
        try {
            jsonStr = responseBody.string();
            return JsonParser.parseString(jsonStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Map<String, String> matchIdsToData(JsonElement rootElement, Set<String> assetIdSet) {
        Map<String, String> assetIdNameMap = new HashMap<>(651);

        for(JsonElement assetElement : rootElement.getAsJsonArray()) {
            JsonObject assetJsonObj = assetElement.getAsJsonObject();
            String assetId = assetJsonObj.get("id").getAsString();
            String assetName = assetJsonObj.get("name").getAsString();

            if(assetIdSet.contains(assetId)) {
                assetIdNameMap.put(assetId, assetName);
            }
        }

        return assetIdNameMap;
    }

    private static List<ErrorInfo> saveNamesToDB(Map<String, String> assetIdNameMap) {
        List<ErrorInfo> errorInfoList = new ArrayList<>();

        for(Map.Entry<String, String> entry : assetIdNameMap.entrySet()) {
            HashMap<String, AttributeValue> primaryKeyItem = new HashMap<>(2);
            HashMap<String, AttributeValueUpdate> assetNameUpdate = new HashMap<>(2);

            String assetId = entry.getKey();
            String assetName = entry.getValue();

            primaryKeyItem.put("id", AttributeValue.builder().s(assetId).build());
            AttributeValue assetNameAttrValue = AttributeValue.builder().s(assetName).build();
            assetNameUpdate.put("name", AttributeValueUpdate.builder().value(assetNameAttrValue).action(AttributeAction.PUT).build());

            UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(primaryKeyItem)
                    .attributeUpdates(assetNameUpdate)
                    .build();

            UpdateItemResponse response = dynamoDbClient.updateItem(updateItemRequest);
            if(!response.sdkHttpResponse().isSuccessful()) {
                errorInfoList.add(new ErrorInfo("Error calling AddAssetNames.saveNamesToDB() for assetId: " + assetId + " and assetName: " + assetName));
            }
        }
        return errorInfoList;
    }
}
