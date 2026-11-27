package com.lichader.hipdanext.utils;

import android.content.Context;
import android.text.TextUtils;

import com.vdurmont.emoji.EmojiParser;

import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.bean.ThreadBean;
import com.lichader.hipdanext.bean.ThreadListBean;
import com.lichader.hipdanext.service.NotiHelper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class HiParserThreadList {

    public static ThreadListBean parse(Context context, HiSettingsHelper settings,
                                       NotiHelper notiHelper, Document doc) {

        notiHelper.fetchNotificationAsync(context, doc);
        HiSettingsHelper.updateMobileNetworkStatus(context);

        ThreadListBean threads = new ThreadListBean();

        //parse uid and re-set username if necessary
        if (TextUtils.isEmpty(settings.getUid())) {
            Elements spaceES = doc.select("#umenu cite a");
            if (spaceES.size() == 1) {
                String spaceUrl = spaceES.first().attr("href");
                if (!TextUtils.isEmpty(spaceUrl)) {
                    String uid = Utils.getMiddleString(spaceUrl, "space.php?uid=", "&");
                    String username = Utils.nullToText(spaceES.first().text()).trim();
                    if (!TextUtils.isEmpty(uid)
                            && TextUtils.isDigitsOnly(uid)
                            && !settings.getUid().equals(uid)) {
                        //re-set username if it is not exactly SAME to user inputted with case sensitive
                        if (!settings.getUsername().equals(username)
                                && settings.getUsername().equalsIgnoreCase(username))
                            settings.setUsername(username);
                        //uid will be setted later
                        threads.setUid(uid);
                    }
                }
            }
        }

        Elements tbodyES = doc.select("tbody[id]");
        for (int i = 0; i < tbodyES.size(); ++i) {

            threads.setParsed(true);

            Element tbodyE = tbodyES.get(i);
            ThreadBean thread = new ThreadBean();

            boolean sticky = false;
            Element folderTd = tbodyE.select("td.folder").first();
            if (folderTd != null) {
                Element imgEl = folderTd.select("img").first();
                sticky = imgEl != null && imgEl.attr("src").contains("/pin_");
            }
            thread.setSticky(sticky);
            if (sticky && !settings.isShowStickThreads()) {
                continue;
            }

            boolean isPoll = false;
            Element iconTd = tbodyE.select("td.icon").first();
            if (iconTd != null) {
                Element imgEl = iconTd.select("img").first();
                isPoll = imgEl != null && imgEl.attr("src").contains("/poll");
            }
            thread.setPoll(isPoll);

            Element subjectTh = tbodyE.select("th.subject").first();
            if (subjectTh == null)
                continue;
            Elements titleES = subjectTh.select("span a");
            if (titleES.size() == 0)
                continue;

            Element titleLink = titleES.first();
            String title = titleLink.text();
            String tid = Utils.getMiddleString(titleLink.attr("href"), "tid=", "&");
            thread.setTid(tid);
            thread.setTitle(EmojiParser.parseToUnicode(title));

            String linkStyle = titleLink.attr("style");
            if (!TextUtils.isEmpty(linkStyle)) {
                thread.setTitleColor(Utils.getMiddleString(linkStyle, "color:", "").trim());
            }

            Elements typeES = subjectTh.select("em a");
            if (typeES.size() > 0) {
                thread.setType(typeES.text());
            }

            if (folderTd != null) {
                Elements threadIsNewES = folderTd.select("img");
                if (threadIsNewES.size() > 0) {
                    String imgSrc = Utils.nullToText(threadIsNewES.first().attr("src"));
                    thread.setNew(imgSrc.contains("new"));
                }
            }

            /*  author, authorId and create_time  */
            Elements authorES = tbodyE.select("td.author");
            if (authorES.size() == 0) {
                continue;
            }
            Elements authorciteAES = authorES.first().select("cite a");
            if (authorciteAES.size() == 0) {
                continue;
            }
            String author = authorciteAES.first().text();
            thread.setAuthor(author);
            if (settings.isInBlacklist(author)) {
                continue;
            }

            String userLink = authorciteAES.first().attr("href");
            if (userLink.length() < "space.php?uid=".length()) {
                continue;
            }
            String authorId = Utils.getMiddleString(userLink, "uid=", "&");
            thread.setAuthorId(authorId);

            thread.setAvatarUrl(HiUtils.getAvatarUrlByUid(authorId));

            Elements threadCreateTimeES = authorES.first().select("em");
            if (threadCreateTimeES.size() == 0) {
                continue;
            }
            String threadCreateTime = threadCreateTimeES.first().text();
            thread.setTimeCreate(threadCreateTime);

            Elements threadUpdateTimeES = tbodyE.select("td.lastpost em a");
            if (threadUpdateTimeES.size() > 0) {
                String threadUpdateTime = threadUpdateTimeES.first().text();
                thread.setTimeUpdate(threadUpdateTime);
            }

            /*  comments and views  */
            Elements nums = tbodyE.select("td.nums");
            if (nums.size() == 0) {
                continue;
            }
            Elements comentsES = nums.first().select("strong");
            if (comentsES.size() == 0) {
                continue;
            }
            String comments = comentsES.first().text();
            thread.setCountCmts(comments);

            Elements viewsES = nums.first().select("em");
            if (viewsES.size() == 0) {
                continue;
            }
            String views = viewsES.first().text();
            thread.setCountViews(views);

            // lastpost
            Elements lastpostciteES = tbodyE.select("td.lastpost cite");
            if (lastpostciteES.size() == 0) {
                continue;
            }
            String lastpost = lastpostciteES.first().text();
            thread.setLastPost(lastpost);

            // attachment and picture
            Elements attachs = tbodyE.select("img.attach");
            for (int j = 0; j < attachs.size(); j++) {
                Element attach = attachs.get(j);
                String attach_img_url = attach.attr("src");
                if (attach_img_url.isEmpty()) {
                    continue;
                }
                if (attach_img_url.endsWith("image_s.gif")) {
                    thread.setWithPic(true);
                }
            }

            //get max page number
            Elements pages = tbodyE.select("span.threadpages a");
            int maxPage = 1;
            if (pages.size() > 0) {
                Element pageLink = pages.get(pages.size() - 1);
                if (!TextUtils.isEmpty(pageLink.attr("href"))) {
                    String lastPage = Utils.getMiddleString(pageLink.attr("href"), "page=", "&");
                    if (!TextUtils.isEmpty(lastPage) && TextUtils.isDigitsOnly(lastPage))
                        maxPage = Utils.parseInt(lastPage);
                }
            }
            thread.setMaxPage(maxPage);

            threads.add(thread);
        }

        return threads;
    }

}
