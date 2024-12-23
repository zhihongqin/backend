package org.example.backend.controller.user;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.backend.entity.others.DetectionAPI;
import org.example.backend.service.others.DetectionAPIService;
import org.example.backend.service.others.ReportService;
import org.example.backend.util.ReportUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.apache.commons.codec.binary.Base64.encodeBase64;

import org.example.backend.entity.others.Report;

@RestController
@RequestMapping("/api/ai")
public class AIDetectionController {

  @Autowired private ReportService reportService;

  @Autowired private ReportUtil reportUtil;

  @Autowired private DetectionAPIService detectionAPIService;

  // 日志输出
  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(AIDetectionController.class);

  @PostMapping("/detect")
  public ResponseEntity<String> detect(@RequestBody Map<String, String> body) {
    String childId = body.get("childId");
    String imageUrl = body.get("imageUrl");
    String type = body.get("type");

    // 更新API调用次数
    DetectionAPI detectionAPI = detectionAPIService.selectByType(type);
    detectionAPI.setNumber(detectionAPI.getNumber()+1);
    detectionAPIService.updateDetectionAPI(detectionAPI);

    Report report = new Report();
    report.setChildId(childId);

    report.setReportType(type);
    report.setState("检测中");
    report.setUrl(imageUrl);
    report.setAllowState("disallow");
    report.setReadState("unread");
    int reportId = reportService.insertReport(report);
    // Start the async task
    int result =detectAsync(imageUrl, reportId);
    if (result == 0) {
      return ResponseEntity.ok("已提交检测，正在处理...");
    } else if (result == 1){
      reportService.deleteByReportId(reportId);
      return ResponseEntity.badRequest().body("图片大小不符合要求");
    } else {
      reportService.deleteByReportId(reportId);
      return ResponseEntity.badRequest().body("检测失败");
    }
  }

  @Async
  public int detectAsync(String imageUrl, int reportId) {
    // API产品路径
    String requestUrl = "https://ibody.market.alicloudapi.com/ai_market/ai_universal/zheng_mian/v1";
    // 阿里云APPCODE
    String appcode = "b6d089b896044471b323d68e5f26f812";

    // 定义请求头
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "APPCODE " + appcode);
    headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

    // 创建请求体
    Map<String, String> myBody = new HashMap<>();

    // 将imageUrl转换为服务器上的文件路径
    String baseDirectory =
        System.getProperty("user.dir")
            + File.separator
            + "uploads"
            + File.separator
            + "images"
            + File.separator;
    String fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
    String imgFile = baseDirectory + fileName;

    // 定义最大允许的文件大小为1.5MB（以字节为单位）
    final long MAX_FILE_SIZE = (long) (1.5 * 1024 * 1024);
    // 启用BASE64编码方式进行识别
    String imgBase64;
    try {
      File file = new File(imgFile);
      if (!file.exists() || file.length() > MAX_FILE_SIZE) {
        logger.error("图片大小不符合");
        return 1; // Return early if image does not exist
      }

      // 读取图片文件并进行Base64编码
      byte[] content = new byte[(int) file.length()];
      try (FileInputStream finPutStream = new FileInputStream(file)) {
        int bytesRead = finPutStream.read(content);
        if (bytesRead != content.length) {
          return 1; // Return early if reading image fails
        }
      }
      imgBase64 = new String(encodeBase64(content));
    } catch (IOException e) {
      return 1; // Handle the exception appropriately
    }

    // 构建请求体
    myBody.put("IMAGE", imgBase64);
    myBody.put("IMAGE_TYPE", "0");

    // 发送POST请求
    try {
      String response = post(requestUrl, headers, myBody);
      Report report = reportService.selectByReportId(reportId);
      if (response != null) { // 判断是否成功
        report = reportUtil.generateReportFromJson(response, report);
        logger.info("AIDetect Response: {}", response);
        report.setState("检测完成");
        reportService.updateReport(report);
        return 0;
      } else {
        report.setState("检测失败");
        reportService.updateReport(report);
        return 2;
      }
    } catch (IOException e) {
      // Log the exception or handle accordingly
      return 2;
    }
  }

  // The post method remains unchanged
  public String post(String url, Map<String, String> headers, Map<String, String> body)
      throws IOException {
    HttpClient client = new HttpClient();
    PostMethod postMethod = new PostMethod(url);

    // 设置请求头
    for (String key : headers.keySet()) {
      postMethod.addRequestHeader(key, headers.get(key));
    }

    // 构建请求体
    List<NameValuePair> bodyPair = new ArrayList<>();
    for (String key : body.keySet()) {
      bodyPair.add(new NameValuePair(key, body.get(key)));
    }
    NameValuePair[] bodyKvs = new NameValuePair[body.size()];
    postMethod.setRequestBody(bodyPair.toArray(bodyKvs));

    // 发送请求
    int code = client.executeMethod(postMethod);
    if (code == 200) {
      return postMethod.getResponseBodyAsString();
    } else {
      logger.error("AIDetect Post Error code: {}", code);
      return null;
    }
  }
}
