#  Sonargraph Code Quality Report

**Tool used:** Sonargraph by hello2morrow  
**Scope:** Full project  
**Date:** 2025-07-13

---

## BÁO CÁO ĐÁNH GIÁ CHẤT LƯỢNG MÃ NGUỒN (TIẾNG VIỆT)

###  1. Phân tích mã nguồn
| Chỉ số | Giá trị | Nhận xét |
|--------|--------|----------|
| Mật độ lỗi (Issue Density) | 0.00 | ✅ Không có vấn đề được phát hiện |
| Dòng mã trùng lặp | 0 | ✅ Không có mã trùng lặp |
| Mã dư thừa (Redundant Code) | 0% | ✅ Mã sạch |
| Chỉ số nợ cấu trúc (Structural Debt) | 0 | ✅ Không có nợ kỹ thuật |

---

###  2. Tính kết nối và phụ thuộc
| Chỉ số | Giá trị | Nhận xét |
|--------|--------|----------|
| ACD (Độ phụ thuộc TB) | 5.08 | ⚠️ Có thể cải thiện bằng cách giảm phụ thuộc |
| CCD (Độ phụ thuộc tổng thể) | 716 | 📈 Hơi cao khi hệ thống lớn dần |
| NCCD (CCD chuẩn hoá) | 0.82 | ✅ Dưới 1, vẫn ổn |
| Maintainability Level | 92.54 | ✅ Rất cao |
| Propagation Cost | 3.60 | ✅ Thấp, tốt |

---

###  3. Độ phức tạp
| Chỉ số | Giá trị | Nhận xét |
|--------|--------|----------|
| Độ phức tạp trung bình (McCabe) | 1.65 | ✅ Rất thấp, code dễ hiểu |
| Số phương thức phức tạp | 0 | ✅ Không có phương thức vượt ngưỡng |

---

###  4. Vòng lặp phụ thuộc (Cyclic Dependency)
| Chỉ số | Giá trị | Nhận xét |
|--------|--------|----------|
| Số vòng lặp phụ thuộc | 0 | ✅ Không có |
| Độ rối (%) | 0.00 | ✅ Thiết kế rất sạch |

---

###  5. Quy mô hệ thống
| Chỉ số | Giá trị |
|--------|---------|
| Dòng mã (LOC) | 6,735 |
| Số Component | 141 |
| Số Package Java | 20 |
| Tổng số file nguồn | 141 |

---

###  Tổng kết
- ✅ **Code sạch, maintainability cao, không có duplication, debt hoặc cyclic dependency**
- ⚠️ **ACD có thể được cải thiện để giảm coupling giữa các thành phần**
- 📌 **Phù hợp để mở rộng tiếp mà không lo nợ kỹ thuật**

---