package API_BoPhieu.service.email;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import API_BoPhieu.entity.Event;
import API_BoPhieu.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void sendPasswordResetEmail(User user, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        String htmlContent = buildPasswordResetEmailTemplate(user.getName(), resetUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("no-reply@eventmanagement.com");
            helper.setTo(user.getEmail());
            helper.setSubject("Yêu cầu đặt lại mật khẩu cho tài khoản Event Management");
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Lỗi khi gửi email: " + e.getMessage(), e);
        }
    }

    private String buildPasswordResetEmailTemplate(String userName, String resetUrl) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <meta http-equiv="X-UA-Compatible" content="IE=edge">
                    <title>Đặt lại mật khẩu</title>
                </head>
                <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f4f4f7; line-height: 1.6;">
                    <table role="presentation" style="width: 100%; border-collapse: collapse; background-color: #f4f4f7; padding: 20px 0;">
                        <tr>
                            <td align="center" style="padding: 20px 0;">
                                <table role="presentation" style="max-width: 600px; width: 100%; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);">
                                    <!-- Header với gradient -->
                                    <tr>
                                        <td style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 40px 30px; text-align: center;">
                                            <h1 style="margin: 0; color: #ffffff; font-size: 28px; font-weight: 700; letter-spacing: -0.5px;">
                                                🔒 Đặt lại mật khẩu
                                            </h1>
                                        </td>
                                    </tr>

                                    <!-- Content -->
                                    <tr>
                                        <td style="padding: 40px 30px;">
                                            <p style="margin: 0 0 20px 0; color: #333333; font-size: 16px;">
                                                Chào <strong style="color: #667eea;">%s</strong>,
                                            </p>

                                            <p style="margin: 0 0 20px 0; color: #555555; font-size: 15px;">
                                                Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.
                                                Vui lòng nhấp vào nút bên dưới để đặt lại mật khẩu của bạn.
                                            </p>

                                            <!-- CTA Button -->
                                            <table role="presentation" style="margin: 30px 0; width: 100%%;">
                                                <tr>
                                                    <td align="center">
                                                        <a href="%s" style="display: inline-block; padding: 14px 32px; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: #ffffff; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 16px; box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4); transition: transform 0.2s;">
                                                            Đặt lại mật khẩu
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>

                                            <!-- Alternative link -->
                                            <p style="margin: 20px 0 10px 0; color: #888888; font-size: 13px; text-align: center;">
                                                Hoặc copy và dán link này vào trình duyệt:
                                            </p>
                                            <p style="margin: 0 0 30px 0; color: #667eea; font-size: 12px; word-break: break-all; text-align: center; padding: 10px; background-color: #f8f9fa; border-radius: 6px;">
                                                %s
                                            </p>

                                            <!-- Warning box -->
                                            <div style="background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; border-radius: 4px; margin: 25px 0;">
                                                <p style="margin: 0; color: #856404; font-size: 14px;">
                                                    <strong>⚠️ Lưu ý:</strong> Liên kết này sẽ hết hạn sau <strong>15 phút</strong>.
                                                    Nếu bạn không yêu cầu điều này, vui lòng bỏ qua email này và đảm bảo tài khoản của bạn được bảo mật.
                                                </p>
                                            </div>

                                            <p style="margin: 25px 0 0 0; color: #555555; font-size: 14px;">
                                                Nếu nút không hoạt động, bạn có thể copy và dán URL ở trên vào thanh địa chỉ trình duyệt.
                                            </p>
                                        </td>
                                    </tr>

                                    <!-- Footer -->
                                    <tr>
                                        <td style="background-color: #f8f9fa; padding: 30px; text-align: center; border-top: 1px solid #e9ecef;">
                                            <p style="margin: 0 0 10px 0; color: #6c757d; font-size: 14px;">
                                                Trân trọng,<br>
                                                <strong style="color: #667eea;">Đội ngũ Event Management</strong>
                                            </p>
                                            <p style="margin: 15px 0 0 0; color: #adb5bd; font-size: 12px;">
                                                Email này được gửi tự động, vui lòng không trả lời email này.
                                            </p>
                                        </td>
                                    </tr>
                                </table>

                                <!-- Bottom spacing -->
                                <table role="presentation" style="max-width: 600px; width: 100%%; margin: 20px auto 0;">
                                    <tr>
                                        <td style="text-align: center; padding: 20px 0;">
                                            <p style="margin: 0; color: #adb5bd; font-size: 12px;">
                                                © 2025 Event Management. All rights reserved.
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """
                .formatted(userName, resetUrl, resetUrl);
    }

    @Override
    public void sendEventJoinNotificationEmail(User user, Event event) {
        String htmlContent = buildEventJoinNotificationEmailTemplate(user, event);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("no-reply@eventmanagement.com");
            helper.setTo(user.getEmail());
            helper.setSubject("🎉 Chúc mừng! Bạn đã tham gia sự kiện: " + event.getTitle());
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException(
                    "Lỗi khi gửi email thông báo tham gia sự kiện: " + e.getMessage(), e);
        }
    }

    private String buildEventJoinNotificationEmailTemplate(User user, Event event) {
        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");
        ZonedDateTime startZoned = event.getStartTime().atZone(zoneId);
        ZonedDateTime endZoned = event.getEndTime().atZone(zoneId);

        int dayOfMonth = startZoned.getDayOfMonth();
        int month = startZoned.getMonthValue();
        int year = startZoned.getYear();
        int dayOfWeek = startZoned.getDayOfWeek().getValue();

        String[] vietnameseDays =
                {"", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"};
        String[] vietnameseMonths = {"", "tháng 1", "tháng 2", "tháng 3", "tháng 4", "tháng 5",
                "tháng 6", "tháng 7", "tháng 8", "tháng 9", "tháng 10", "tháng 11", "tháng 12"};

        String startDate = String.format("%s, ngày %d %s năm %d", vietnameseDays[dayOfWeek],
                dayOfMonth, vietnameseMonths[month], year);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String startTime = startZoned.format(timeFormatter);
        String endTime = endZoned.format(timeFormatter);

        String eventUrl = frontendUrl + "/events/" + event.getId();

        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <meta http-equiv="X-UA-Compatible" content="IE=edge">
                    <title>Thông báo tham gia sự kiện</title>
                </head>
                <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background: linear-gradient(135deg, #f5f7fa 0%%, #c3cfe2 100%%); line-height: 1.6;">
                    <table role="presentation" style="width: 100%%; border-collapse: collapse; padding: 40px 20px;">
                        <tr>
                            <td align="center" style="padding: 20px 0;">
                                <table role="presentation" style="max-width: 650px; width: 100%%; margin: 0 auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);">
                                    <tr>
                                        <td style="background: linear-gradient(135deg, #FF6B6B 0%%, #4ECDC4 50%%, #FFE66D 100%%); padding: 50px 30px; text-align: center; position: relative; overflow: hidden;">
                                            <div style="position: absolute; top: -50px; right: -50px; width: 200px; height: 200px; background: rgba(255, 255, 255, 0.1); border-radius: 50%%; z-index: 0;"></div>
                                            <div style="position: absolute; bottom: -30px; left: -30px; width: 150px; height: 150px; background: rgba(255, 255, 255, 0.1); border-radius: 50%%; z-index: 0;"></div>
                                            <div style="position: relative; z-index: 1;">
                                                <div style="font-size: 64px; margin-bottom: 15px;">🎉</div>
                                                <h1 style="margin: 0; color: #ffffff; font-size: 32px; font-weight: 800; letter-spacing: -0.5px; text-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);">
                                                    Chúc mừng!
                                                </h1>
                                                <p style="margin: 10px 0 0 0; color: #ffffff; font-size: 18px; font-weight: 500; opacity: 0.95;">
                                                    Bạn đã tham gia sự kiện thành công
                                                </p>
                                            </div>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td style="padding: 45px 35px;">
                                            <p style="margin: 0 0 25px 0; color: #2c3e50; font-size: 18px; font-weight: 600;">
                                                Chào <strong style="color: #FF6B6B;">%s</strong> 👋,
                                            </p>

                                            <p style="margin: 0 0 30px 0; color: #555555; font-size: 16px; line-height: 1.8;">
                                                Chúng tôi rất vui mừng thông báo rằng bạn đã được đăng ký tham gia sự kiện thành công!
                                                Dưới đây là thông tin chi tiết về sự kiện:
                                            </p>

                                            <div style="background: linear-gradient(135deg, #f8f9fa 0%%, #e9ecef 100%%); border-left: 5px solid #4ECDC4; border-radius: 12px; padding: 30px; margin: 30px 0; box-shadow: 0 4px 15px rgba(0, 0, 0, 0.08);">
                                                <h2 style="margin: 0 0 20px 0; color: #2c3e50; font-size: 24px; font-weight: 700; display: flex; align-items: center;">
                                                    <span style="margin-right: 10px; font-size: 28px;">📅</span>
                                                    %s
                                                </h2>

                                                <div style="margin: 20px 0; padding: 15px; background-color: #ffffff; border-radius: 8px; border-left: 4px solid #FF6B6B;">
                                                    <p style="margin: 0 0 8px 0; color: #6c757d; font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; font-weight: 600;">
                                                        📝 Mô tả
                                                    </p>
                                                    <p style="margin: 0; color: #2c3e50; font-size: 15px; line-height: 1.7;">
                                                        %s
                                                    </p>
                                                </div>

                                                <table role="presentation" style="width: 100%%; margin: 20px 0; border-collapse: collapse;">
                                                    <tr>
                                                        <td style="padding: 12px 0; border-bottom: 1px solid #e9ecef;">
                                                            <div style="display: flex; align-items: center;">
                                                                <span style="font-size: 20px; margin-right: 12px;">🕐</span>
                                                                <div>
                                                                    <p style="margin: 0; color: #6c757d; font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; font-weight: 600;">
                                                                        Thời gian
                                                                    </p>
                                                                    <p style="margin: 5px 0 0 0; color: #2c3e50; font-size: 16px; font-weight: 600;">
                                                                        %s, từ %s đến %s
                                                                    </p>
                                                                </div>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <td style="padding: 12px 0; border-bottom: 1px solid #e9ecef;">
                                                            <div style="display: flex; align-items: center;">
                                                                <span style="font-size: 20px; margin-right: 12px;">📍</span>
                                                                <div>
                                                                    <p style="margin: 0; color: #6c757d; font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; font-weight: 600;">
                                                                        Địa điểm
                                                                    </p>
                                                                    <p style="margin: 5px 0 0 0; color: #2c3e50; font-size: 16px; font-weight: 600;">
                                                                        %s
                                                                    </p>
                                                                </div>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                </table>
                                            </div>

                                            <table role="presentation" style="margin: 35px 0; width: 100%%;">
                                                <tr>
                                                    <td align="center">
                                                        <a href="%s" style="display: inline-block; padding: 16px 40px; background: linear-gradient(135deg, #FF6B6B 0%%, #4ECDC4 100%%); color: #ffffff; text-decoration: none; border-radius: 50px; font-weight: 700; font-size: 16px; box-shadow: 0 6px 20px rgba(255, 107, 107, 0.4); transition: transform 0.2s; letter-spacing: 0.5px;">
                                                            ✨ Xem chi tiết sự kiện
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>

                                            <div style="background: linear-gradient(135deg, #FFF9E6 0%%, #FFE66D 100%%); border-left: 5px solid #FFE66D; border-radius: 10px; padding: 20px; margin: 30px 0; box-shadow: 0 3px 10px rgba(255, 230, 109, 0.3);">
                                                <p style="margin: 0; color: #856404; font-size: 15px; line-height: 1.7;">
                                                    <strong style="font-size: 18px;">💡 Lưu ý quan trọng:</strong><br>
                                                    • Vui lòng có mặt đúng giờ tại địa điểm sự kiện<br>
                                                    • Mang theo giấy tờ tùy thân để xác nhận danh tính<br>
                                                    • Nếu có thay đổi, chúng tôi sẽ thông báo qua email
                                                </p>
                                            </div>

                                            <p style="margin: 30px 0 0 0; color: #6c757d; font-size: 15px; line-height: 1.7;">
                                                Chúng tôi rất mong được gặp bạn tại sự kiện! Nếu bạn có bất kỳ câu hỏi nào,
                                                đừng ngần ngại liên hệ với chúng tôi.
                                            </p>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td style="background: linear-gradient(135deg, #f8f9fa 0%%, #e9ecef 100%%); padding: 35px; text-align: center; border-top: 2px solid #e9ecef;">
                                            <p style="margin: 0 0 12px 0; color: #495057; font-size: 16px; font-weight: 600;">
                                                Trân trọng,<br>
                                                <span style="background: linear-gradient(135deg, #FF6B6B 0%%, #4ECDC4 50%%, #FFE66D 100%%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; font-weight: 700;">
                                                    Đội ngũ Event Management
                                                </span>
                                            </p>
                                            <p style="margin: 20px 0 0 0; color: #adb5bd; font-size: 13px; line-height: 1.6;">
                                                Email này được gửi tự động, vui lòng không trả lời email này.<br>
                                                Nếu bạn có thắc mắc, vui lòng liên hệ qua hệ thống quản lý sự kiện.
                                            </p>
                                        </td>
                                    </tr>
                                </table>

                                <table role="presentation" style="max-width: 650px; width: 100%%; margin: 25px auto 0;">
                                    <tr>
                                        <td style="text-align: center; padding: 20px 0;">
                                            <p style="margin: 0; color: #adb5bd; font-size: 12px;">
                                                © 2025 Event Management. All rights reserved.
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """
                .formatted(user.getName(), event.getTitle(), event.getDescription(), startDate,
                        startTime, endTime, event.getLocation(), eventUrl);
    }

}
