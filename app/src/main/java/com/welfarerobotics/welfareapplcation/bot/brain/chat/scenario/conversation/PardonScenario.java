package com.welfarerobotics.welfareapplcation.bot.brain.chat.scenario.conversation;

import com.welfarerobotics.welfareapplcation.bot.Mouth;

import java.io.IOException;

/**
 * @author : Hyunwoong
 * @when : 5/28/2019 2:28 AM
 * @homepage : https://github.com/gusdnd852
 */
public class PardonScenario {
    public static void process(String speech) throws IOException {
        Mouth.get().say();
    }
}
