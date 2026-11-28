package com.lichader.hipdanext.utils;

import android.content.Context;

import com.lichader.hipdanext.bean.SimpleListBean;
import com.lichader.hipdanext.bean.SimpleListItemBean;
import com.lichader.hipdanext.job.SimpleListJob;
import com.lichader.hipdanext.service.NotiHelper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class HiParser {

    public static SimpleListBean parseSimpleList(Context context, NotiHelper notiHelper, int type,
                                                 Document doc) {

        notiHelper.fetchNotificationAsync(context, doc);

        switch (type) {
            case SimpleListJob.TYPE_SMS_DETAIL:
                return parseSmsDetail(doc);
        }

        return null;
    }

    public static SimpleListBean parseSMS(Document doc) {
        if (doc == null) {
            return null;
        }

        Elements pmlistES = doc.select("ul.pm_list");
        if (pmlistES.size() < 1) {
            return null;
        }

        SimpleListBean list = new SimpleListBean();
        Elements liES = pmlistES.first().select("li");
        for (int i = 0; i < liES.size(); ++i) {
            Element liE = liES.get(i);
            SimpleListItemBean item = new SimpleListItemBean();

            // avatar
            Elements avatarES = liE.select("a.avatar");
            if (avatarES.size() > 0) {
                Elements avatarImgES = avatarES.first().select("img");
                if (avatarImgES.size() > 0) {
                    item.setAvatarUrl(avatarImgES.first().attr("src"));
                }
            }

            // author and author uid
            Elements pciteES = liE.select("p.cite");
            if (pciteES.size() == 0) {
                continue;
            }
            Elements citeES = pciteES.first().select("cite");
            if (citeES.size() == 0) {
                continue;
            }
            item.setAuthor(citeES.first().text());
            Elements uidAES = citeES.first().select("a");
            if (uidAES.size() == 0) {
                continue;
            }
            String uid = uidAES.first().attr("href");
            item.setUid(Utils.getMiddleString(uid, "uid=", "&"));

            // time
            item.setTime(pciteES.first().ownText());

            // new
            Elements imgES = pciteES.first().select("img");
            if (imgES.size() > 0) {
                if (imgES.first().attr("src").contains(HiUtils.NewPMImage)) {
                    item.setNew(true);
                }
            }

            // info
            Elements summaryES = liE.select("div.summary");
            if (summaryES.size() == 0) {
                continue;
            }
            item.setTitle(summaryES.first().text());

            list.add(item);
        }

        return list;
    }

    private static SimpleListBean parseSmsDetail(Document doc) {
        if (doc == null) {
            return null;
        }

        //get my uid and username
        Elements uidMenuES = doc.select("#umenu cite a.noborder");
        if (uidMenuES.size() < 1) {
            return null;
        }
        String mySpaceUrl = Utils.nullToText(uidMenuES.first().attr("href"));
        String myUid = Utils.getMiddleString(mySpaceUrl, "uid=", "&");
        String myUsername = uidMenuES.first().text();

        Elements smslistES = doc.select("li.s_clear");
        if (smslistES.size() < 1) {
            return null;
        }

        SimpleListBean list = new SimpleListBean();
        for (int i = 0; i < smslistES.size(); ++i) {
            Element smsE = smslistES.get(i);
            SimpleListItemBean item = new SimpleListItemBean();

            // author
            Elements pciteES = smsE.select("p.cite");
            if (pciteES.size() == 0) {
                continue;
            }
            Elements citeES = pciteES.first().select("cite");
            if (citeES.size() == 0) {
                continue;
            }
            item.setAuthor(citeES.first().text());

            // avatar
            Elements avatarES = smsE.select("a.avatar");
            if (avatarES.size() > 0) {
                if (item.getAuthor().equals(myUsername)) {
                    item.setUid(myUid);
                } else {
                    String spaceUrl = Utils.nullToText(avatarES.first().attr("href"));
                    item.setUid(Utils.getMiddleString(spaceUrl, "uid=", "&"));
                }
                item.setAvatarUrl(HiUtils.getAvatarUrlByUid(item.getUid()));
            }

            // time
            item.setTime(pciteES.first().ownText());

            // info
            Elements summaryES = smsE.select("div.summary");
            if (summaryES.size() == 0) {
                continue;
            }
            item.setInfo(summaryES.first().html());

            // new
            Elements imgES = pciteES.first().select("img");
            if (imgES.size() > 0) {
                if (imgES.first().attr("src").contains(HiUtils.NewPMImage)) {
                    item.setNew(true);
                }
            }

            list.add(item);
        }

        return list;
    }

    public static String parseFormhash(Document doc) {
        if (doc == null) {
            return null;
        }
        Elements inputs = doc.select("input[name=formhash]");
        if (inputs.size() > 0)
            return inputs.get(0).val();
        return null;
    }

    public static String parseErrorMessage(Document doc) {
        Elements errors = doc.select("div.alert_error");
        if (errors.size() > 0) {
            Element el = errors.first();
            el.select("a").remove();
            return el.text();
        }
        return null;
    }
}
