# Vietnamese Telex — bộ gõ tiếng Việt builtin cho Minecraft

NeoForge mod cho **Minecraft 1.21.1** tích hợp sẵn bộ gõ **Telex**, chỉ hoạt động
khi bạn đang gõ chữ trong **khung chat** — không xung đột với phím điều khiển
(WASD, phím tắt…) và không cần bật/tắt phần mềm gõ tiếng Việt bên ngoài
(EVKey, UniKey, OpenKey…).

## Tính năng

- **Chỉ hoạt động trong khung chat** (kể cả khi gõ lệnh `/`). Ngoài màn hình game, mọi phím vẫn bình thường.
- Quy tắc Telex đầy đủ:
  - Thanh điệu: `s` sắc · `f` huyền · `r` hỏi · `x` ngã · `j` nặng.
  - Dấu phụ: `aa` → â · `aw` → ă · `ee` → ê · `oo` → ô · `ow` → ơ · `uw` → ư · `w` đầu từ → ư · `dd` → đ.
- **Gõ dấu sau vẫn nhận**: gõ `chao` rồi `s` → cháo.
- **Backspace gỡ dấu từng bước** như bộ gõ thật: dưỡng ⌫ dương ⌫ duon…
- Kiểm tra cấu trúc âm tiết (~170 vần): chữ Anh như hello, english, thanks **giữ nguyên**, không bị thành ký tự có dấu.
- Gõ tắt vần lướt: duong → dương, nguoi → người, dien → diên, cuoc → cuộc.
- Vần hiếm bị nhường cho từ thông dụng (muons → *mướn*); khi đó gõ đủ: muoons → muốn, muoos → muộn.
- Phím tắt bật/tắt: **Right Shift** (đổi được trong Options → Controls), hoạt động cả lúc đang mở chat.
- Chỉ báo nhỏ [TELEX] ở góc trái trên khi chat mở (tắt được trong config).

## Cách dùng

1. Build mod, copy jar trong build/libs/ vào thư mục mods/.
2. Nhấn T hoặc / để mở chat, gõ Telex như thường: xin chaof → xin chào (hoặc gõ xong chao rồi thêm f).
3. **Right Shift** để bật/tắt nhanh.

> Mẹo: vì mod thay thế IME hệ thống, hãy tắt EVKey/UniKey (hoặc chuyển chế độ tiếng Anh) khi chơi để tránh biến đổi hai lần.

## Build

Yêu cầu JDK 21.

```bash
./gradlew build          # Windows: gradlew.bat build
```

Kết quả: build/libs/vietnamesetelex-1.0.0.jar (bỏ qua file -sources).
Unit test của engine chạy cùng build, hoặc riêng: ./gradlew test

## Kiến trúc

| File | Vai trò |
| ---- | ------- |
| TelexEngine.java | Port Java của **OpenKey engine** (OpenKey-master/Sources/OpenKey/engine): buffer phím + cờ TONE/TONEW/MARK/CAPS, bảng mẫu _vowel/_vowelForMark/_consonantD, checkGrammar tự đặt lại dấu sau mỗi phím |
| ClientHooks.java | Bắt ScreenEvent.CharacterTyped / KeyPressed / Render khi ChatScreen mở, quản lý buffer thô của từ đang gõ |
| VietnameseTelexClient.java | Entry point client: đăng ký config, keybind, event listeners |
| TelexConfig.java | Config phía client (bật/tắt, chỉ báo) |

Không dùng mixin — toàn bộ can thiệp input đi qua NeoForge events nên không dễ vỡ khi update phiên bản.

Phần mở rộng riêng của mod so với OpenKey: tiền-chuẩn hóa `khong → khoong`
và tự thêm modifier cho vần lướt gõ tắt (`duong → duongw`, `dien → dieen`).

## Giới hạn đã biết

- Chỉ biến đổi khi con trỏ ở **cuối** dòng nhập (cách gõ phổ biến khi chat).
- Từ chứa số/ký tự đặc biệt sẽ ngắt việc theo dõi từ đang gõ (an toàn, không lỗi).
- Một số vần hiếm cần gõ dạng đầy đủ (oo, ow, uw) như ghi ở trên.
