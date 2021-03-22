import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        boolean closeApp = false;
        System.out.println("Welcome to the Cryptars Util!");

        while(!closeApp) {
            displayMenu();
            String input = in.nextLine().trim();
            switch (input) {
                case "1":
                    UpdateIcons.updateAllSupportedIcons();
                    break;
                case "2":
                    UpdateTradingPairs.addNewPair("", null);
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
        System.out.println("\n---------------MENU---------------");
        System.out.println("[1] Update Asset Icons");
        System.out.println("[2] Update Trading Pairs");
        System.out.println("[3] Add Asset Names To DB");
        System.out.println("[X] Close Application");
        System.out.println("----------------------------------");
        System.out.print("Select option (1, 2, etc): ");
    }
}
