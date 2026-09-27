package com.glint.server.biz.sms;

final class PhoneMask {

    private PhoneMask() {
    }

    static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
