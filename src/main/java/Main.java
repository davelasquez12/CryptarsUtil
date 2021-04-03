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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {
    private static DynamoDbClient dynamoDbClient;
    private static CoinGeckoAPI coinGeckoAPI;
    private static Map<String, Asset[]> assetMap; //key = symbol, value = array of Asset objects

    public static void main(String[] args) {
        System.out.println("Initializing...");
        initDynamoDbClient();
        initCoinGeckoApi();
        boolean status = initAssetMap();

        if(status) {
            System.out.println("Initialization complete!");
        }
        else {
            System.out.println("Initialization failed!");
            return;
        }

        Scanner in = new Scanner(System.in);
        boolean closeApp = false;

        System.out.println("\nWelcome to the Cryptars Util!");

        while(!closeApp) {
            displayMenu();
            String input = in.nextLine().trim();
            switch (input) {
                case "1":
                    UpdateIcons.updateAllSupportedIcons();
                    break;
                case "2":
                    new UpdateTradingPairs().run();
                    break;
                case "3":
                    AddAssetNames.run();
                case "X":
                case "x":
                    closeApp = true;
                    break;
                default:
                    System.out.println("Input error, please try again!\n");
            }
        }
    }

    private static void displayMenu() {
        System.out.println("\n---------------MAIN MENU---------------");
        System.out.println("[1] Update Asset Icons");
        System.out.println("[2] Update Trading Pairs");
        System.out.println("[3] Add Asset Names To DB");
        System.out.println("[X] Close Application");
        System.out.println("-----------------------------------------");
        System.out.print("Select option (1, 2, etc): ");
    }

    private static void initDynamoDbClient() {
        if(dynamoDbClient == null) {
            dynamoDbClient = DynamoDbClient.builder()
                    .region(Region.US_EAST_2)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        }
    }


    private static void initCoinGeckoApi() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.coingecko.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        coinGeckoAPI = retrofit.create(CoinGeckoAPI.class);
    }

    private static boolean initAssetMap() {
        Response<ResponseBody> response = null;
        assetMap = new HashMap<>(10000);
        try {
            System.out.println("Getting asset list from CoinGeckoAPI...");
            response = coinGeckoAPI.getCryptoIdsAndNames().execute();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(response != null && response.isSuccessful() && response.body() != null) {
            System.out.println("CoinGecko API called successfully!");
            JsonElement responseJsonElement = convertResponseToJsonElement(response.body());

            if(responseJsonElement != null) {
                return populateMap(responseJsonElement);
            }
        }

        return false;
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

    public static boolean populateMap(JsonElement responseJson) {
        System.out.print("Populating assetMap...");

        int assetCount = 0;

        for(JsonElement assetElement : responseJson.getAsJsonArray()) {
            JsonObject assetJsonObj = assetElement.getAsJsonObject();
            String assetId = assetJsonObj.get("id").getAsString();
            String assetSymbol =  assetJsonObj.get("symbol").getAsString();
            String assetName = assetJsonObj.get("name").getAsString();

            Asset asset = new Asset(assetId, assetName, assetSymbol);
            assetCount++;
            Asset[] assetsByKey = assetMap.get(assetSymbol);

            if(assetsByKey == null) {
                assetsByKey = new Asset[1];
                assetsByKey[0] = asset;

                assetMap.put(assetSymbol, assetsByKey);
            }
            else {
                //System.out.println("Duplicate symbol found: id = " + assetId + ", symbol = " + assetSymbol);
                int newLength = assetsByKey.length + 1;

                Asset[] assets = new Asset[newLength];

                System.arraycopy(assetsByKey, 0, assets, 0, assetsByKey.length);

                assets[newLength - 1] = asset;
                assetMap.put(assetSymbol, assets);
            }
        }

        if(assetMap.isEmpty()) {
            System.out.println("\nERROR: assetMap is empty after attempting to populate it.");
            return false;
        }else {
            System.out.println("assetMap populated successfully!");
            System.out.println("Asset count = " + assetCount);
            return true;
        }
    }

    public static Map<String, Asset[]> getAssetMap() {
        return assetMap;
    }

    public static DynamoDbClient getDynamoDbClient() {
        return dynamoDbClient;
    }
}
