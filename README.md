# HiPDA Next

**HiPDA论坛非官方客户端**

HiPDA Next 是 HiPDA 论坛的非官方 Android 客户端，基于 HiPDA·NG 分叉继续维护。

项目地址 https://github.com/lichader/hipda-next

多谢 HiPDA·NG 作者绿皮怪兽（GreenSkinMonster）及原作者 jejer 的分享  
HiPDA·NG 发布帖 https://www.4d4y.com/forum/viewthread.php?tid=1579403  
原项目发布于 https://www.4d4y.com/forum/viewthread.php?tid=1408844

# 开发者注意

请把 hipad/fabric.properties.example 复制为 fabric.properties ，否则编译可能失败

# 权限要求说明
- android.permission.INTERNET 必需，用于访问HiPDA论坛服务器及图片、更新服务器等。
- android.permission.ACCESS_NETWORK_STATE 必需，用于判断不同网络环境条件实施不同图片加载策略。
- android.permission.WRITE_EXTERNAL_STORAGE 可选，用于保存下载文件，或者选取上传文件、自定义字体等。
- android.permission.CAMERA 可选，用于直接拍照上传图片。

# Credits
- HiPDA简洁版 - https://bitbucket.org/jejer/hipda/
- Android Cookie Store - https://github.com/gotev/android-cookie-store
- Android Iconics - https://github.com/mikepenz/Android-Iconics
- Android Priority Job Queue - https://github.com/yigit/android-priority-jobqueue
- android-job - https://github.com/evernote/android-job
- BubbleLayout - https://github.com/MasayukiSuda/BubbleLayout
- Calligraphy - https://github.com/InflationX/Calligraphy
- Emoji - https://github.com/vanniktech/Emoji
- Emoji Java - https://github.com/vdurmont/emoji-java
- EventBus - https://github.com/greenrobot/EventBus
- Glide - https://github.com/bumptech/glide
- Jsoup - http://jsoup.org/
- MaterialDrawer - https://github.com/mikepenz/MaterialDrawer
- Matisse - https://github.com/zhihu/Matisse
- OkHttp - https://github.com/square/okhttp
- Subsampling Scale Image View - https://github.com/davemorrissey/subsampling-scale-image-view
