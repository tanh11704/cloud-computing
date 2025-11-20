# Thiết Kế Giao Diện Mobile - Ứng Dụng Quản Lý Sự Kiện VKU

## 1. Tổng Quan Thiết Kế

### 1.1 Phong Cách Thiết Kế

- **Phong cách chính**: Modern Academic - kết hợp chuyên nghiệp học đường với yếu tố trẻ trung, năng động.
- **Đối tượng**: Sinh viên và giảng viên VKU
- **Tone**: Thân thiện, dễ tiếp cận nhưng vẫn trang trọng
- **UI Pattern**: Material Design 3 với các yếu tố hiện đại như Neumorphism nhẹ và gradients cho depth.

### 1.2 Bộ Màu Sắc Chủ Đạo

#### Primary Colors

```
Xanh Dương Chính (VKU Blue): #1D4ED8 (Blue-700) - Sáng hơn so với #1E3A8A, giữ bản sắc VKU.
Xanh Dương Nhạt: #60A5FA (Blue-400) - Dùng cho highlights, buttons, và backgrounds.
Xanh Dương Rất Nhạt: #EFF6FF (Blue-50) - Nền thoáng cho cards và sections.
Gradient Primary: Linear-gradient từ #1D4ED8 đến #60A5FA - Dùng cho headers, buttons, hoặc accents.
```

#### Secondary Colors

```
Vàng Chính: #FBBF24 (Amber-400) - Sáng hơn #F59E0B, tạo cảm giác ấm áp, trẻ trung.
Vàng Nhạt: #FEF3C7 (Amber-100) - Nền pastel cho cards hoặc notifications.
Vàng Đậm: #D97706 (Amber-600) - Accents mạnh hoặc hover states.
Gradient Secondary: Linear-gradient từ #FBBF24 đến #FEF3C7 - Dùng cho FAB hoặc quick actions.
```

#### Accent Colors

```
Đỏ Chính: #EF4444 (Red-500) - Sáng hơn #DC2626, thu hút nhưng không quá mạnh.
Đỏ Nhạt: #FEE2E2 (Red-100) - Nền cho alerts hoặc badges.
Gradient Accent: Linear-gradient từ #EF4444 đến #FEE2E2 - Dùng cho danger buttons hoặc highlights.
```

#### Neutral Colors

```
Trắng: #FFFFFF
Xám Nhạt: #F9FAFB (CoolGray-50) - Nền chính, sáng và thoáng.
Xám Trung: #6B7280 (CoolGray-500) - Text phụ hoặc icons.
Xám Đậm: #374151 (CoolGray-700) - Borders hoặc text phụ.
Đen: #111827 (CoolGray-900) - Text chính, đảm bảo contrast cao.
```

### 1.3 Typography

#### Heading Fonts

```
H1: 24px, Bold, Letter-spacing: -0.5px
H2: 20px, Bold, Letter-spacing: -0.25px
H3: 18px, SemiBold
H4: 16px, SemiBold
H5: 14px, Medium
```

#### Body Text

```
Body Large: 16px, Inter, Line-height: 24px
Body Medium: 14px, Inter, Line-height: 20px
Body Small: 12px, Inter, Line-height: 18px
Caption: 10px, Inter, Line-height: 14px
```

#### Font Family

- **Primary**: Inter, SF Pro Display (iOS), Roboto (Android)
- **Secondary**: Nunito Sans (cho các element friendly)

### 1.4 Spacing System

- space-xs (extra small): 4px
- space-sm (small): 8px
- space-md (medium): 16px
- space-lg (large): 24px
- space-xl (extra large): 32px

## 2. Component Library

### 2.1 Buttons

#### Primary Button

```
Background: Gradient Primary (#1D4ED8 đến #60A5FA)
Text: White
Padding: 12px 24px
Border-radius: 12px
Font: 14px, Medium
Shadow: 0px 2px 8px rgba(29, 78, 216, 0.2)
Hover/Press: Scale 0.98, opacity 0.9, gradient sáng hơn 10%
```

#### Secondary Button

```
Background: Transparent
Border: 2px solid #60A5FA
Text: #1D4ED8
Padding: 10px 24px
Border-radius: 12px
Font: 14px, Medium
Hover/Press: Background #EFF6FF, shadow 0px 2px 6px rgba(96, 165, 250, 0.2)
```

#### Danger Button

```
Background: Gradient Accent (#EF4444 đến #FEE2E2)
Text: White
Padding: 12px 24px
Border-radius: 12px
Font: 14px, Medium
Shadow: 0px 2px 8px rgba(239, 68, 68, 0.2)
Hover/Press: Darken gradient 10%, scale effect
```

#### Floating Action Button (FAB)

```
Background: Gradient Secondary (#FBBF24 đến #FEF3C7)
Size: 56x56px
Icon: White (SVG hiện đại)
Shadow: 0px 4px 12px rgba(251, 191, 36, 0.3)
Hover/Press: Pulse animation (scale 1.1 rồi về 1.0)
```

### 2.2 Input Fields

#### Standard Input

```
Border: 1px solid #D1D5DB
Border-radius: 12px
Padding: 12px 16px
Font: 14px, Regular
Focus state: Border color #60A5FA, shadow 0px 0px 0px 3px rgba(96, 165, 250, 0.2)
Placeholder: #6B7280, italic nhẹ
```

#### Search Input

```
Background: #F9FAFB
Border: none
Border-radius: 32px
Padding: 10px 40px 10px 16px
Icon: Search icon (right side, màu #6B7280, scale nhẹ khi focus)
Focus: Background #EFF6FF, border 1px solid #60A5FA
```

### 2.3 Cards

#### Event Card

```
Background: White với gradient nhẹ từ #FFFFFF đến #EFF6FF
Border-radius: 16px
Shadow: 0px 4px 12px rgba(0, 0, 0, 0.05)
Padding: 16px
Margin-bottom: 16px
Hover: Lift effect (translateY -2px), shadow tăng nhẹ
```

#### Info Card

```
Background: #FEF3C7
Border-radius: 16px
Border-left: 4px solid #FBBF24
Padding: 16px
Shadow: 0px 2px 8px rgba(251, 191, 36, 0.1)
Hover: Gradient shift (#FEF3C7 đến #FFF7ED)
```

### 2.4 Navigation

#### Bottom Navigation

```
Height: 70px
Background: White, shadow 0px -2px 4px rgba(0, 0, 0, 0.05)
Items: 4-5 items maximum
Active color: #1D4ED8, icon scale 1.1 animation
Inactive color: #6B7280
Transition: Smooth fade và scale
```

#### Tab Navigation

```
Height: 48px
Background: White
Indicator: Gradient Primary (#1D4ED8 đến #60A5FA), 3px height, rounded ends
Text: 14px, Medium
Hover: Text color #1D4ED8, underline animation
```
