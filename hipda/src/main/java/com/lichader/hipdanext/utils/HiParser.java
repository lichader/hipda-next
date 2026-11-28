package com.lichader.hipdanext.utils;

import android.content.Context;
import android.text.TextUtils;

import com.lichader.hipdanext.bean.SimpleListBean;
import com.lichader.hipdanext.bean.SimpleListItemBean;
import com.lichader.hipdanext.job.SimpleListJob;
import com.lichader.hipdanext.service.NotiHelper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class HiParser {

    public static SimpleListBean parseSimpleList(Context context, NotiHelper notiHelper, int type,
                                                 Document doc, boolean isFullTextSearch) {

        notiHelper.fetchNotificationAsync(context, doc);

        switch (type) {
            case SimpleListJob.TYPE_SMS_DETAIL:
                return parseSmsDetail(doc);
            case SimpleListJob.TYPE_SEARCH:
                if (isFullTextSearch) {
                    return parseSearchFullText(doc);
                } else {
                    parseSearch(doc);
                }
            case SimpleListJob.TYPE_SEARCH_USER_THREADS:
                return parseSearch(doc);
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

    private static SimpleListBean parseSearch(Document doc) {
        if (doc == null) {
            return null;
        }

        SimpleListBean list = new SimpleListBean();
        int last_page = 1;

        //if this is the last page, page number is in <strong>
        Elements pagesES = doc.select("div.pages_btns div.pages a");
        pagesES.addAll(doc.select("div.pages_btns div.pages strong"));
        String searchIdUrl;
        if (pagesES.size() > 0) {
            searchIdUrl = pagesES.first().attr("href");
            list.setSearchId(Utils.getMiddleString(searchIdUrl, "searchid=", "&"));
            for (Node n : pagesES) {
                int tmp = Utils.getIntFromString(((Element) n).text());
                if (tmp > last_page) {
                    last_page = tmp;
                }
            }
        }
        list.setMaxPage(last_page);

        Elements tbodyES = doc.select("tbody");
        for (int i = 0; i < tbodyES.size(); ++i) {
            Element tbodyE = tbodyES.get(i);
            SimpleListItemBean item = new SimpleListItemBean();

            Elements subjectES = tbodyE.select("tr th.subject a");
            if (subjectES.size() == 0) {
                continue;
            }

            Element titleLink = subjectES.first();
            String href = titleLink.attr("href");
            item.setTid(Utils.getMiddleString(href, "tid=", "&"));
            item.setTitle(titleLink.text());

            Elements authorAES = tbodyE.select("tr td.author cite a");
            if (authorAES.size() == 0) {
                continue;
            }
            item.setAuthor(authorAES.first().text());

            String spaceUrl = authorAES.first().attr("href");
            if (!TextUtils.isEmpty(spaceUrl)) {
                String uid = Utils.getMiddleString(spaceUrl, "uid=", "&");
                item.setAvatarUrl(HiUtils.getAvatarUrlByUid(uid));
            }

            Elements timeES = tbodyE.select("tr td.author em");
            if (timeES.size() > 0) {
                item.setTime(timeES.first().text());
            }

            Elements forumES = tbodyE.select("tr td.forum");
            if (forumES.size() > 0) {
                item.setForum(forumES.first().text());
            }

            list.add(item);
        }

        return list;
    }

    private static SimpleListBean parseSearchFullText(Document doc) {
        if (doc == null) {
            return null;
        }

        SimpleListBean list = new SimpleListBean();
        int last_page = 1;

        //if this is the last page, page number is in <strong>
        Elements pagesES = doc.select("div.pages_btns div.pages a");
        pagesES.addAll(doc.select("div.pages_btns div.pages strong"));
        String searchIdUrl;
        if (pagesES.size() > 0) {
            searchIdUrl = pagesES.first().attr("href");
            list.setSearchId(Utils.getMiddleString(searchIdUrl, "searchid=", "&"));
            for (Node n : pagesES) {
                int tmp = Utils.getIntFromString(((Element) n).text());
                if (tmp > last_page) {
                    last_page = tmp;
                }
            }
        }
        list.setMaxPage(last_page);

        Elements tbodyES = doc.select("table.datatable tr");
        for (int i = 0; i < tbodyES.size(); ++i) {
            Element trowE = tbodyES.get(i);
            SimpleListItemBean item = new SimpleListItemBean();

            Elements subjectES = trowE.select("div.sp_title a");
            if (subjectES.size() == 0) {
                continue;
            }
            item.setTitle(subjectES.first().text());
            //gotopost.php?pid=12345
            String postUrl = Utils.nullToText(subjectES.first().attr("href"));
            item.setPid(Utils.getMiddleString(postUrl, "pid=", "&"));
            if (TextUtils.isEmpty(item.getPid())) {
                continue;
            }

            Elements contentES = trowE.select("div.sp_content");
            if (contentES.size() > 0) {
                item.setInfo(contentES.text());
            }

//            <div class="sp_theard">
//            <span class="sp_w200">版块: <a href="forumdisplay.php?fid=2">Discovery</a></span>
//            <span>作者: <a href="space.php?uid=189027">tsonglin</a></span>
//            <span>查看: 1988</span>
//            <span>回复: 56</span>
//            <span class="sp_w200">最后发表: 2015-4-4 21:58</span>
//            </div>
            Elements postInfoES = trowE.select("div.sp_theard span");
            if (postInfoES.size() != 5) {
                continue;
            }
            Elements authorES = postInfoES.get(1).select("a");
            if (authorES.size() > 0) {
                item.setAuthor(authorES.first().text());
                String spaceUrl = authorES.first().attr("href");
                if (!TextUtils.isEmpty(spaceUrl)) {
                    String uid = Utils.getMiddleString(spaceUrl, "uid=", "&");
                    item.setAvatarUrl(HiUtils.getAvatarUrlByUid(uid));
                }
            }

            item.setTime(Utils.getMiddleString(postInfoES.get(4).text(), ":", "&"));

            Elements forumES = postInfoES.get(0).select("a");
            if (forumES.size() > 0)
                item.setForum(forumES.first().text());

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
