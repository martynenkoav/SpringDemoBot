package com.example.springdemobot.service;

import com.example.springdemobot.config.BotConfig;
import com.example.springdemobot.model.User;
import com.example.springdemobot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.WebhookBot;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;

    final BotConfig botConfig;

    static final String YES_BUTTON = "YES_BUTTON";
    static final String NO_BUTTON = "NO_BUTTON";

    public TelegramBot(BotConfig botConfig) throws TelegramApiException {
        this.botConfig = botConfig;
        List<BotCommand> listOfCommands = new ArrayList();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/application", "submit your application"));
        listOfCommands.add(new BotCommand("/getplaces", "get the closest places"));
        listOfCommands.add(new BotCommand("/help", "description of the bot"));
        listOfCommands.add(new BotCommand("/fractions", "list of fractions"));
        listOfCommands.add(new BotCommand("/sendLocation", "send your location"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting list for menu" + e.getMessage());
        }
    }
    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()){
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch(messageText){
                case "/start":
                    try {
                        registerUser(update.getMessage());
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case "/fractions":
                    SendMessage msg = new SendMessage();
                    msg.setChatId(String.valueOf(chatId));
                    msg.setText("в меню рядом с кнопкой отправки доступен список фракций");
                    msg.setReplyMarkup(this.makeKeyBoard());
                    try {
                        execute(msg);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                    break;
                case "/location":
                    askForLocation(chatId);
                    break;
                default:
                    try {
                        sendMessage(chatId, "Sorry");
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                        log.error("Error occurred:" + e.getMessage());
                    }
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.equals(YES_BUTTON)) {
                String text = "You pressed YES button";
                executeEditMessageText(text, chatId, messageId);
            } else if (callbackData.equals(NO_BUTTON)) {
                String text = "You pressed NO button";
                executeEditMessageText(text, chatId, messageId);
            }
        }


    }

    private void askForLocation(long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText("Do you want to share location?");

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();

        yesButton.setText("Yes");
        yesButton.setCallbackData(YES_BUTTON);

        var noButton = new InlineKeyboardButton();

        noButton.setText("No");
        noButton.setCallbackData(NO_BUTTON);

        rowInLine.add(yesButton);
        rowInLine.add(noButton);

        rowsInLine.add(rowInLine);

        markupInLine.setKeyboard(rowsInLine);
        msg.setReplyMarkup(markupInLine);

        executeMessage(msg);

    }

    private void executeMessage(SendMessage message){
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    private void registerUser(Message msg) {
        if (userRepository.findById(msg.getChatId()).isEmpty()){
            Long charId = msg.getChatId();
            Chat chat = msg.getChat();

            User user = new User();
            user.setChatId(charId);
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            user.setLatitude(msg.getLocation().getLatitude());
            user.setLongitude(msg.getLocation().getLongitude());
            userRepository.save(user);
        }
    }

    private void startCommandReceived(long chatId, String name) throws TelegramApiException {
        String answer = "Hi, " + name + ", nice to meet you!";
        sendMessage(chatId, answer);
        log.info("Replied to user:" + name);

    }

    private void sendMessage(long chatId, String textToSend) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error occurred:" + e.getMessage());
        }
    }

    private ReplyKeyboardMarkup makeKeyBoard(){
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();

        row.add("food");
        row.add("paper");
        keyboardRows.add(row);
        row = new KeyboardRow();

        row.add("plastics");
        row.add("wood");
        row.add("glass");

        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        return keyboardMarkup;
    }
}
