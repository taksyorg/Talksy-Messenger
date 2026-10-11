package com.talksy.messenger.ui;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public final class SupportFaq {
    private SupportFaq() {}

    public static final class Item {
        public final String question;
        public final String answer;
        Item(String q, String a) { question = q; answer = a; }
    }

    private static boolean isRu(Context c) {
        return "ru".equals(c.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    public static List<Item> items(Context c) {
        boolean ru = isRu(c);
        List<Item> l = new ArrayList<>();
        if (ru) {
            l.add(new Item("Не приходят уведомления",
                    "Проверьте, что уведомления включены: Настройки → Уведомления.\n\nНа Xiaomi, Huawei, Oppo, Realme и некоторых других прошивках система сама останавливает фоновые службы. Разрешите Talksy автозапуск и уберите ограничение батареи в настройках телефона."));
            l.add(new Item("Сообщение не отправляется (красный значок)",
                    "Проверьте интернет. Сообщение не доставится, если собеседник заблокировал вас или ограничил личные сообщения.\n\nЕсли причина не в этом, напишите оператору: так мы быстрее найдём ошибку."));
            l.add(new Item("Как сменить имя или @username",
                    "Откройте свой профиль и нажмите на имя или username. Username должен быть свободен и не повторять чужой."));
            l.add(new Item("Потерял доступ к аккаунту",
                    "Войдите по e-mail: код придёт на почту. Если у вас анонимный номер +888 или +999, войдите по номеру и паролю, выданному при покупке.\n\nЕсли нет доступа ни к почте, ни к паролю, свяжитесь с оператором и расскажите, когда вы последний раз пользовались аккаунтом."));
            l.add(new Item("Что такое сквозное шифрование (E2E)",
                    "По умолчанию сообщения шифруются ключом сервера. Если включить E2E в Настройки → Конфиденциальность, сообщения расшифровываются только у собеседника. Тогда отправка иногда ждёт, пока у собеседника появится ключ шифрования."));
            l.add(new Item("Как заблокировать человека или ограничить, кто мне пишет",
                    "Заблокированных можно посмотреть и убрать в Настройки → Конфиденциальность → Заблокированные. Там же настраивается, кто может вам писать."));
            l.add(new Item("Как включить прокси, если Talksy не открывается",
                    "Настройки → Прокси и обход блокировок. Поддерживаются SOCKS5 и собственный протокол Talksy. Данные для подключения даёт владелец прокси."));
            l.add(new Item("Что такое Wallet и Premium",
                    "Wallet — внутренний кошелёк Talksy: он открывается в самом верху Настроек. Баланс используется для переводов, подарков и покупок.\n\nTalksy Premium оформляется в Настройки → Talksy Premium (в нижней части списка)."));
            l.add(new Item("Как удалить аккаунт",
                    "Автоудаление при неактивности включается в Настройки → Конфиденциальность. Немедленное удаление делает оператор: напишите ему с того аккаунта, который нужно удалить."));
            l.add(new Item("Есть ли скрытые функции?",
                    "Говорят, поиск по чатам понимает секретные команды со слэшем. Какие именно, придётся искать самим. Для начала попробуйте /secrets."));
        } else {
            l.add(new Item("Notifications do not arrive",
                    "Make sure notifications are on: Settings → Notifications.\n\nOn Xiaomi, Huawei, Oppo, Realme and some other firmware the system stops background services on its own. Allow Talksy to autostart and remove the battery restriction in phone settings."));
            l.add(new Item("Message will not send (red icon)",
                    "Check your internet. A message is not delivered if the other person blocked you or limited private messages.\n\nIf that is not the reason, write to the operator: it helps us find the bug faster."));
            l.add(new Item("How to change my name or @username",
                    "Open your profile and tap your name or username. The username must be free and not match someone else's."));
            l.add(new Item("I lost access to my account",
                    "Sign in by e-mail: the code arrives in your mailbox. If you have an anonymous +888 or +999 number, sign in with the number and the password issued at purchase.\n\nIf you have neither the mailbox nor the password, contact the operator and tell us when you last used the account."));
            l.add(new Item("What is end-to-end encryption (E2E)",
                    "By default messages are encrypted with the server key. If you turn on E2E in Settings → Privacy, messages can be decrypted only by the other person. Sending may then wait until they have an encryption key."));
            l.add(new Item("How to block someone or limit who can message me",
                    "You can see and remove blocked users in Settings → Privacy → Blocked. Who may write to you is configured there too."));
            l.add(new Item("How to use a proxy if Talksy will not open",
                    "Settings → Proxy and bypass. SOCKS5 and the Talksy protocol are supported. The connection details come from the proxy owner."));
            l.add(new Item("What are Wallet and Premium",
                    "Wallet is the internal Talksy wallet at the very top of Settings. The balance is used for transfers, gifts and purchases.\n\nTalksy Premium is in Settings → Talksy Premium (lower part of the list)."));
            l.add(new Item("How to delete my account",
                    "Auto-delete on inactivity is in Settings → Privacy. Immediate deletion is done by the operator: write from the account you want removed."));
            l.add(new Item("Are there hidden features?",
                    "They say chat search understands secret commands starting with a slash. You will have to find which ones. Try /secrets for a start."));
        }
        return l;
    }
}
