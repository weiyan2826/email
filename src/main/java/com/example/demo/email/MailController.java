package com.example.demo.email;


import com.myblog.model.User;
import com.myblog.service.serviceimp.UserServiceImp;
import com.myblog.utils.MD5Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
public class MailController {

    @Autowired
    private JavaMailSender sender;


    private static final Logger logger = LoggerFactory.getLogger(MailController.class);

    @RequestMapping(value = "/email")
    @ResponseBody
    public Map forgetPass(HttpServletRequest request, String userName) {
        User users = userServiceImp.findUserPhone("");
        Map map = new HashMap<String, String>();
        String msg = "";
        if (users == null) {              //用户名不存在
            msg = "用户名不存在,你不会忘记用户名了吧?";
            map.put("msg", msg);
            return map;
        }
        try {
            String secretKey = UUID.randomUUID().toString();  //密钥
            Timestamp outDate = new Timestamp(System.currentTimeMillis() + 30 * 60 * 1000);//30分钟后过期
            long date = outDate.getTime() / 1000 * 1000;                  //忽略毫秒数
            users.setValidataCode(secretKey);
            users.setRegisterDate(String.valueOf(outDate));
            userServiceImp.updateUserInfo(users);    //保存到数据库
            String key = users.getUserName() + "$" + date + "$" + secretKey;
            MD5Util md5Util = new MD5Util();
            String digitalSignature = md5Util.encode(key);                 //数字签名

            String emailTitle = "密码找回";
            String path = request.getContextPath();
            String basePath = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + path + "/";
            String resetPassHref = basePath + "hello?sid=" + digitalSignature + "&userName=" + users.getUserPhone();
            String emailContent = "请勿回复本邮件.点击下面的链接,重设密码<br/><a href=" + resetPassHref + " target='_BLANK'>点击我重新设置密码</a>" +
                    "<br/>tips:本邮件超过30分钟,链接将会失效，需要重新申请'找回密码'" + key + "\t" + digitalSignature;
            System.out.print(resetPassHref);
            sendHtmlMail(emailContent, emailTitle, "1824693453@qq.com");
            msg = "操作成功,已经发送找回密码链接到您邮箱。请在30分钟内重置密码";

        } catch (Exception e) {
            e.printStackTrace();
            msg = "邮箱不存在？未知错误,联系管理员吧。";
        }
        map.put("msg", msg);
        return map;
    }


    @RequestMapping(value = "hello", method = RequestMethod.GET)
    public ModelAndView checkResetLink(String sid, String userName) {
        ModelAndView model = new ModelAndView("error");
        String msg = "";
        if (sid.equals("") || userName.equals("")) {
            msg = "链接不完整,请重新生成";
            model.addObject("msg", msg);
            return model;
        }
        User users = userServiceImp.findUserPhone("@");
        if (users == null) {
            msg = "链接错误,无法找到匹配用户,请重新申请找回密码.";
            model.addObject("msg", msg);
            return model;
        }

        Timestamp outDate = Timestamp.valueOf(users.getRegisterDate());
        if (outDate.getTime() <= System.currentTimeMillis()) {         //表示已经过期
            msg = "链接已经过期,请重新申请找回密码.";
            model.addObject("msg", msg);
            return model;
        }
        String key = users.getUserName() + "$" + outDate.getTime() / 1000 * 1000 + "$" + users.getValidataCode();          //数字签名
        MD5Util md5Util = new MD5Util();
        String digitalSignature = md5Util.encode(key);
        System.out.println(key + "\t" + digitalSignature);
        if (!digitalSignature.equals(sid)) {
            msg = "链接不正确,是否已经过期了?重新申请吧";
            model.addObject("msg", msg);
            return model;
        }
        model.setViewName("hello");  //返回到修改密码的界面
        model.addObject("userName", userName);
        return model;
    }

    /**
     * 发送html格式的邮件
     *
     * @param to
     * @param subject
     * @param content
     */
    public void sendHtmlMail(String content, String subject, String to) {
        MimeMessage message = sender.createMimeMessage();

        try {
            //true表示需要创建一个multipart message
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom("@163.com");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            sender.send(message);
            System.out.println("html有限已发送");
        } catch (Exception e) {
            System.out.println("发送html邮件时发生异常");
        }
    }
}
