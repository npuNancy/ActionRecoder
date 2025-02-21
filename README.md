# ActionRecoder

## 简介

ActionRecoder 是一个基于 Android 应用程序，用于后台记录用户的操作和对应的截图。

## 界面
界面有3个按钮
- 开始/停止记录按钮：点击后开始记录用户的操作和对应的截图，再次点击停止记录。
- 开启无障碍服务按钮：本APP需要开启无障碍服务才能正常使用。
    - 未开启时点击按钮是$\color{red}{红色高亮}$，点击后会跳转到无障碍服务设置界面。
    - 开启后按钮是绿色高亮，点击后显示"无障碍服务已开启"。
    
- 存储目录。点击后显示存储目录。
    - 存储目录位于/Download/ActionRecoder/下，文件名是时间戳。
![界面截图](Screenshots_20250220_201019\1740053420080.jpg)



## 功能
- 开始记录后，用户每操作一次手机，便会记录一次操作、对应的截图、包名。
- 记录的操作包括点击、长按、窗口切换等（Type还没实现）
- 截图只在点击、长按时记录。
` Timestamp, Time, operation, packageName, screenshotFile `
- `operation`列记录操作，例如CLICK(50,50)， 有些还有额外的Description，例如"关闭推荐内容"
- 记录的数据为 CSV 格式。




| Timestamp | Time| operation | packageName | screenshotFile |
| --------- |-----|-----------|-------------|-----------------|
| 1740053419995 |2025/2/20 20:10|WINDOW_CHANGE|com.miui.home,|
| 1740053420080 |2025/2/20 20:10|CLICK(360， 803);|com.example.actionrecoder,|Screenshots_20250220_201019/1740053420080.jpg|
| 1740053452254 |2025/2/20 20:10|CLICK(360， 1603);Event Description: 主屏幕;|com.android.systemui,|Screenshots_20250220_201019/1740053452254.jpg|
| 1740053452431 |2025/2/20 20:10|WINDOW_CHANGE|com.example.actionrecoder,|
| 1740053452479 |2025/2/20 20:10|WINDOW_CHANGE| com.tencent.mm |


## 数据标注

可以根据动作、包名（可以转成对应的APP名称）、截图，进行数据标注。
使用`<im_start>`和`<im_end>, <intent: >`标记一段动作，例如：

 | Timestamp | Time| operation | packageName | screenshotFile |
| --------- |-----|-----------|-------------|-----------------|
|  <im_start> |-----|-----------|-------------|-----------------|
| 1740053419995 |2025/2/20 20:10|WINDOW_CHANGE|com.miui.home,|
| 1740053420080 |2025/2/20 20:10|CLICK(360， 803);|com.example.actionrecoder,|Screenshots_20250220_201019/1740053420080.jpg|
|  <im_end> | <intent: 打开了淘宝> |-----------|-------------|-----------------|
|  <im_start> |-----|-----------|-------------|-----------------|
| 1740053452254 |2025/2/20 20:10|CLICK(360， 1603);Event Description: 主屏幕;|com.android.systemui,|Screenshots_20250220_201019/1740053452254.jpg|
| 1740053452431 |2025/2/20 20:10|WINDOW_CHANGE|com.example.actionrecoder,|
| 1740053452479 |2025/2/20 20:10|WINDOW_CHANGE| com.tencent.mm |
|  <im_end> | <intent: 打开微信> |-----------|-------------|-----------------|