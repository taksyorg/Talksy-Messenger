package com.talksy.messenger.data;

import com.talksy.messenger.data.model.Country;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Countries {

    private static List<Country> LIST;

    public static List<Country> all() {
        if (LIST == null) {
            LIST = new ArrayList<>();
            LIST.add(new Country("Russia", "7", "🇷🇺", 10, 10));
            LIST.add(new Country("Kazakhstan", "7", "🇰🇿", 10, 10));
            LIST.add(new Country("Ukraine", "380", "🇺🇦", 9, 9));
            LIST.add(new Country("Belarus", "375", "🇧🇾", 9, 9));
            LIST.add(new Country("Georgia", "995", "🇬🇪", 9, 9));
            LIST.add(new Country("Armenia", "374", "🇦🇲", 8, 8));
            LIST.add(new Country("Azerbaijan", "994", "🇦🇿", 9, 9));
            LIST.add(new Country("Uzbekistan", "998", "🇺🇿", 9, 9));
            LIST.add(new Country("Kyrgyzstan", "996", "🇰🇬", 9, 9));
            LIST.add(new Country("Tajikistan", "992", "🇹🇯", 9, 9));
            LIST.add(new Country("Turkmenistan", "993", "🇹🇲", 8, 8));
            LIST.add(new Country("Moldova", "373", "🇲🇩", 8, 8));
            LIST.add(new Country("Turkey", "90", "🇹🇷", 10, 10));
            LIST.add(new Country("Germany", "49", "🇩🇪", 10, 11));
            LIST.add(new Country("France", "33", "🇫🇷", 9, 9));
            LIST.add(new Country("United Kingdom", "44", "🇬🇧", 10, 10));
            LIST.add(new Country("Italy", "39", "🇮🇹", 9, 10));
            LIST.add(new Country("Spain", "34", "🇪🇸", 9, 9));
            LIST.add(new Country("Poland", "48", "🇵🇱", 9, 9));
            LIST.add(new Country("Netherlands", "31", "🇳🇱", 9, 9));
            LIST.add(new Country("Portugal", "351", "🇵🇹", 9, 9));
            LIST.add(new Country("Sweden", "46", "🇸🇪", 9, 9));
            LIST.add(new Country("Finland", "358", "🇫🇮", 9, 10));
            LIST.add(new Country("Norway", "47", "🇳🇴", 8, 8));
            LIST.add(new Country("Czech Republic", "420", "🇨🇿", 9, 9));
            LIST.add(new Country("Austria", "43", "🇦🇹", 10, 11));
            LIST.add(new Country("Switzerland", "41", "🇨🇭", 9, 9));
            LIST.add(new Country("Greece", "30", "🇬🇷", 10, 10));
            LIST.add(new Country("Serbia", "381", "🇷🇸", 8, 9));
            LIST.add(new Country("Bulgaria", "359", "🇧🇬", 9, 9));
            LIST.add(new Country("Romania", "40", "🇷🇴", 9, 9));
            LIST.add(new Country("Cyprus", "357", "🇨🇾", 8, 8));
            LIST.add(new Country("United States", "1", "🇺🇸", 10, 10));
            LIST.add(new Country("Canada", "1", "🇨🇦", 10, 10));
            LIST.add(new Country("Mexico", "52", "🇲🇽", 10, 10));
            LIST.add(new Country("Brazil", "55", "🇧🇷", 10, 11));
            LIST.add(new Country("Argentina", "54", "🇦🇷", 10, 10));
            LIST.add(new Country("Israel", "972", "🇮🇱", 9, 9));
            LIST.add(new Country("United Arab Emirates", "971", "🇦🇪", 9, 9));
            LIST.add(new Country("Saudi Arabia", "966", "🇸🇦", 9, 9));
            LIST.add(new Country("Egypt", "20", "🇪🇬", 10, 10));
            LIST.add(new Country("India", "91", "🇮🇳", 10, 10));
            LIST.add(new Country("China", "86", "🇨🇳", 11, 11));
            LIST.add(new Country("Japan", "81", "🇯🇵", 10, 10));
            LIST.add(new Country("South Korea", "82", "🇰🇷", 9, 10));
            LIST.add(new Country("Thailand", "66", "🇹🇭", 9, 9));
            LIST.add(new Country("Vietnam", "84", "🇻🇳", 9, 10));
            LIST.add(new Country("Indonesia", "62", "🇮🇩", 9, 12));
            LIST.add(new Country("Australia", "61", "🇦🇺", 9, 9));
            LIST.add(new Country("New Zealand", "64", "🇳🇿", 8, 9));
            LIST.add(new Country("South Africa", "27", "🇿🇦", 9, 9));
            LIST.add(new Country("Nigeria", "234", "🇳🇬", 10, 10));
            LIST.add(new Country("Latvia", "371", "🇱🇻", 8, 8));
            LIST.add(new Country("Lithuania", "370", "🇱🇹", 8, 8));
            LIST.add(new Country("Estonia", "372", "🇪🇪", 7, 8));
        }
        return LIST;
    }

    public static int guessDefaultIndex() {
        String iso = Locale.getDefault().getCountry();
        List<Country> list = all();
        String guessName = isoToName(iso);
        if (guessName != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).name.equals(guessName)) return i;
            }
        }
        return 0;
    }

    private static String isoToName(String iso) {
        if (iso == null) return null;
        switch (iso.toUpperCase(Locale.ROOT)) {
            case "RU": return "Russia";
            case "KZ": return "Kazakhstan";
            case "UA": return "Ukraine";
            case "BY": return "Belarus";
            case "GE": return "Georgia";
            case "AM": return "Armenia";
            case "AZ": return "Azerbaijan";
            case "UZ": return "Uzbekistan";
            case "TR": return "Turkey";
            case "DE": return "Germany";
            case "US": return "United States";
            case "GB": return "United Kingdom";
            default: return null;
        }
    }
}
