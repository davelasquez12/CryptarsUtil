import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class UpdateTradingPairs {
    private static final Scanner in = new Scanner(System.in);
    private String optionSelected;
    private static final String TRADING_PAIRS_TABLE = "TradingPairs";
    private static final String CURRENT_CRYPTO_PRICES_TABLE = "CurrentCryptoPrices";
    private static final String SUPPORTED_ASSETS_TABLE = "CryptarsSupportedAssets";
    private List<ErrorInfo> errorInfoList;

    public void run() {
        errorInfoList = new ArrayList<>();
        boolean closeApp = false;

        while(!closeApp) {
            displayMenu();
            String input = in.nextLine().trim();
            switch (input) {
                case "1":
                    optionSelected = "1";
                    System.out.println("Input format: base_quote1,quote2,...quoteN");
                    InputWrapper inputWrapper = parseInput();
                    if(inputWrapper != null) {
                        addNewPairDataToDB(inputWrapper.base, inputWrapper.quotes);
                        if(!errorInfoList.isEmpty()) {
                            for(ErrorInfo errorInfo : errorInfoList) {
                                System.out.println(errorInfo.getMessage() + "\n");
                            }
                        }
                    }
                    break;
                /*case "2":
                    optionSelected = "2";
                    parseInput(input);
                    addNewQuote("");
                    break;*/
                case "X":
                case "x":
                    closeApp = true;
                    break;
                default:
                    System.out.println("Input error, please try again!\n");
            }

            errorInfoList.clear();
        }
    }

    private InputWrapper parseInput() {
        String input;

        if(optionSelected.equals("1")) {
            String base;
            String quotes;
            Set<String> quotesSet = new HashSet<>(7);
            String[] parsedQuotes;

            while(true) {
                System.out.print("Enter trading pair(s): ");

                try {
                    input = in.nextLine().trim();
                    String[] parsedInput = input.split("_");
                    base = parsedInput[0];
                    quotes = parsedInput[1];
                    parsedQuotes = quotes.replaceAll(" ", "").split(",");
                } catch (Exception e) {
                    System.out.println("Error with provided input. Try again.");
                    continue;
                }

                System.out.println("Inputted trading pair:\n");
                System.out.println("Base: " + base + "\nQuotes: " + Arrays.toString(parsedQuotes) + "\n");
                System.out.print("Is this correct? (y/n): ");
                input = in.nextLine().trim();

                if("y".equalsIgnoreCase(input)) {
                    quotesSet.addAll(Arrays.asList(parsedQuotes));
                    return new InputWrapper(base, quotesSet);
                }
            }
        }

        return null;
    }

    private void addNewPairDataToDB(String base, Set<String> quotes) {
        Asset asset = getAssetByBase(base);

        if(asset != null) {
            Map<String, AttributeValue> tradingPairItem = new HashMap<>(7);
            tradingPairItem.put("id", AttributeValue.builder().s(asset.getId()).build());
            tradingPairItem.put("name", AttributeValue.builder().s(asset.getName()).build());
            tradingPairItem.put("base", AttributeValue.builder().s(asset.getBase()).build());
            tradingPairItem.put("quotes", AttributeValue.builder().ss(quotes).build());

            Put putTradingPairsObj = Put.builder()
                    .tableName(TRADING_PAIRS_TABLE)
                    .item(tradingPairItem)
                    .conditionExpression("attribute_not_exists(id)")
                    .build();
            TransactWriteItem tradingPairWriteItem = TransactWriteItem.builder().put(putTradingPairsObj).build();

            TransactWriteItem supportedAssetsWriteItem = buildSupportedAssetsWriteItem(asset.getId());
            if(supportedAssetsWriteItem == null) {
                System.out.println("ERROR: supportedAssetsWriteItem is null");
                return;
            }

            List<TransactWriteItem> updatedWriteItemsCCP = updateCCPTable(asset, quotes);
            if(updatedWriteItemsCCP == null) {
                System.out.println("ERROR: updatedWriteItemsCCP is null");
                return;
            }

            List<TransactWriteItem> allTransactWriteItems = new ArrayList<>();
            allTransactWriteItems.add(tradingPairWriteItem);
            allTransactWriteItems.add(supportedAssetsWriteItem);
            allTransactWriteItems.addAll(updatedWriteItemsCCP);

            TransactWriteItemsRequest transactWriteItemsRequest = TransactWriteItemsRequest.builder().transactItems(allTransactWriteItems).build();
            TransactWriteItemsResponse writeItemsResponse;
            try {
                writeItemsResponse = Main.getDynamoDbClient().transactWriteItems(transactWriteItemsRequest);
                if(writeItemsResponse != null && writeItemsResponse.sdkHttpResponse().isSuccessful()) {
                    System.out.println("Successfully updated the TradingPairs, CryptarsSupportedAssets, and CurrentCryptoPrices tables with the asset:\n\n" + asset + "\nQuotes: " + quotes + "\n");
                }
                else {
                    errorInfoList.add(new ErrorInfo("Error: Failed to add asset: " + asset));
                }
            }
            catch (ConditionalCheckFailedException e) {
                System.out.println("The asset " + asset.getBase() + " with id \"" + asset.getId() + "\" already exists in the " + TRADING_PAIRS_TABLE + " table.");
                errorInfoList.add(new ErrorInfo(e.getMessage()));
            }
            catch (TransactionCanceledException e) {
                for(CancellationReason cancellationReason : e.cancellationReasons()) {
                    errorInfoList.add(new ErrorInfo("Cancellation reason: " + cancellationReason.message()));
                }
            }
        }
        else {
            System.out.println("Asset not found in Main.assetMap with the provided base OR action was canceled.");
        }
    }

    private TransactWriteItem buildSupportedAssetsWriteItem(String assetIdToAdd) {
        Map<String, AttributeValue> key = new HashMap<>(3);
        key.put("asset_type", AttributeValue.builder().s("crypto").build());

        GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName(SUPPORTED_ASSETS_TABLE)
                .key(key)
                .attributesToGet("id_set")
                .build();

        GetItemResponse response = Main.getDynamoDbClient().getItem(getItemRequest);
        if(response != null && response.sdkHttpResponse().isSuccessful() && response.hasItem()) {
            List<String> currentAssetIds = response.item().get("id_set").ss();  //returns an immutable List so must copy over data to new Set

            if(currentAssetIds != null && !currentAssetIds.isEmpty()) {
                Set<String> updatedAssetIds = new HashSet<>(currentAssetIds.size() * 2 + 1);
                updatedAssetIds.addAll(currentAssetIds);
                updatedAssetIds.add(assetIdToAdd);

                Map<String, AttributeValue> updatedAssetIdListItem = new HashMap<>(5);
                updatedAssetIdListItem.put("asset_type", AttributeValue.builder().s("crypto").build());
                updatedAssetIdListItem.put("id_set", AttributeValue.builder().ss(updatedAssetIds).build());

                Put putSupportedAssetsObj = Put.builder().tableName(SUPPORTED_ASSETS_TABLE)
                        .item(updatedAssetIdListItem)
                        .build();

                return TransactWriteItem.builder().put(putSupportedAssetsObj).build();
            }
        }
        return null;
    }

    /*
    * 1. Get current items from CCP table
    * 2. Add new asset data for each quote specified
    * 3. Save updated items back to CCP table
    * */
    private List<TransactWriteItem> updateCCPTable(Asset asset, Set<String> quotes) {  //todo: move this method to a class UpdateCCPTable
        List<Map<String, AttributeValue>> updatedItems = new ArrayList<>(quotes.size());
        ScanRequest scanRequest = ScanRequest.builder().tableName(CURRENT_CRYPTO_PRICES_TABLE).build();
        ScanResponse scanResponse = Main.getDynamoDbClient().scan(scanRequest);

        if(scanResponse.sdkHttpResponse().isSuccessful() && scanResponse.hasItems()) {
            for(Map<String, AttributeValue> item : scanResponse.items()) {
                String quote = item.get("quote").s();

                if(quotes.contains(quote)) {
                    Map<String, AttributeValue> itemCopy = buildCopyOfItemAndAddNewAsset(item, asset);
                    updatedItems.add(itemCopy);
                }
            }

            if(updatedItems.size() == quotes.size()) {
                List<TransactWriteItem> transactWriteItems = new ArrayList<>(quotes.size());

                for(Map<String, AttributeValue> updatedItem : updatedItems) {
                    Put transactPutObj = Put.builder().tableName(CURRENT_CRYPTO_PRICES_TABLE).item(updatedItem).build();
                    TransactWriteItem transactWriteItem = TransactWriteItem.builder().put(transactPutObj).build();
                    transactWriteItems.add(transactWriteItem);
                }

                return transactWriteItems;
            }
            else {
                errorInfoList.add(new ErrorInfo("ERROR :: updateCCPTable :: 1 or more quotes was not able to be processed, so aborting update to the " + CURRENT_CRYPTO_PRICES_TABLE + " table."));
            }
        }
        return null;
    }

    private Map<String, AttributeValue> buildCopyOfItemAndAddNewAsset(Map<String, AttributeValue> item, Asset assetToAdd) {
        Map<String, AttributeValue> itemCopy = new HashMap<>(item.size() * 2 + 1);

        for(String key : item.keySet()) {
            AttributeValue valueCopy = null;

            if(key.equals("quote")) {
                valueCopy = AttributeValue.builder().s(item.get(key).s()).build();
            }
            else if(key.equals("price_map")){
                Map<String, AttributeValue> priceMap = item.get(key).m();
                Map<String, AttributeValue> priceMapCopy = new HashMap<>(priceMap.size() * 2 + 1);

                for(Map.Entry<String, AttributeValue> entry : priceMap.entrySet()) {
                    priceMapCopy.put(entry.getKey(), AttributeValue.builder().ss(entry.getValue().ss()).build());
                }

                if(!priceMapCopy.containsKey(assetToAdd.getId())) {
                    priceMapCopy.put(assetToAdd.getId(), AttributeValue.builder().ss("_" + assetToAdd.getBase(), "TBD").build()); //setting second value as "TBD" as a placeholder for the price. If no value is provided, GCCP lambda will fail when updating this asset's price for the first time
                    System.out.println("Added " + assetToAdd.getId() + " - " + assetToAdd.getBase() + " to the priceMap for the quote: " + item.get("quote").s());
                }
                else {
                    System.out.println("WARNING :: buildCopyOfItemAndAddNewAsset :: did not add new asset to priceMapCopy since it already contains the asset id attempting to add: " + assetToAdd.getId());
                }
                valueCopy = AttributeValue.builder().m(priceMapCopy).build();
            }

            if(valueCopy != null) {
                itemCopy.put(key, valueCopy);
            }
        }
        return itemCopy;
    }

    private void addNewQuoteToAllAssets() {

    }

    private void addNewQuote(String base) {

    }

    private void addNewQuote(List<String> bases) {

    }

    private Asset getAssetByBase(String base) {
        Asset[] assets = Main.getAssetMap().get(base);
        Asset asset = null;

        if(assets != null) {
            if(assets.length == 1) {
                asset = assets[0];
                System.out.println("Found 1 asset with that base: " + asset.toString());

                while(true) {
                    System.out.print("Continue with adding " + asset.getName() + " to the DB? (y/n): ");
                    String response = in.nextLine().trim();

                    if(response.equalsIgnoreCase("y")) {
                        break;
                    }
                    else if(response.equalsIgnoreCase("n")) {
                        asset = null;
                        break;
                    }else {
                        System.out.println("Incorrect input, please try again.\n");
                    }
                }
            }
            else {
                StringBuilder stringBuilder = new StringBuilder();
                for(int i = 0; i < assets.length; i++) {
                    stringBuilder.append("[").append(i).append("] ")
                            .append(assets[i].getId()).append(" - ")
                            .append(assets[i].getName()).append("\n");
                }

                System.out.println("Found " + assets.length + " assets with that base.");
                int selected = retrieveSelectedInput(stringBuilder.toString(), assets.length);

                if(selected == -1) {
                    return null;
                }
                asset = assets[selected];
            }
        }

        return asset;
    }

    private int retrieveSelectedInput(String assetSelection, int numOptions) {
        while(true) {
            System.out.println("Select the one to add to DB (1, 2, etc) or X to cancel:\n");
            System.out.println(assetSelection);
            System.out.print("Selection: ");
            String input = in.nextLine().trim();

            if(input.equalsIgnoreCase("X")) {
                return -1;
            }

            try {
                int selection = Integer.parseInt(input);
                if(selection >= 0 && selection < numOptions) {
                    return selection;
                } else {
                    System.out.println("Incorrect selection, please try again.\n");
                }
            }catch (Exception e) {
                System.out.println("Incorrect input, please try again.\n");
            }

        }
    }

    private static void displayMenu() {
        System.out.println("\n--------UPDATE TRADING PAIR MENU--------");
        System.out.println("[1] Add new trading pair");
        System.out.println("[2] Add new quote(s)");
        System.out.println("[X] Close Menu");
        System.out.println("------------------------------------------");
        System.out.print("Select option (1, 2, etc): ");
    }

    private static class InputWrapper {
        String base;
        Set<String> quotes;

        InputWrapper(String base, Set<String> quotes) {
            this.base = base;
            this.quotes = quotes;
        }
    }

}
