# -*- coding: utf-8 -*-
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT, WD_TAB_LEADER
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
import os

FONT = 'Sarabun'
BODY = 14.5
DARK = (20, 50, 90)
GRAY = (90, 90, 90)

PAGE_MAP = {}
if os.path.exists('report-assets/toc-pages.txt'):
    for line in open('report-assets/toc-pages.txt', encoding='utf-8-sig'):
        if '=' in line:
            k, v = line.strip().split('=', 1)
            PAGE_MAP[k] = v


def set_font(run, size=BODY, bold=False, name=FONT, color=None):
    run.font.name = name
    run.font.size = Pt(size)
    run.bold = bold
    if color:
        run.font.color.rgb = RGBColor(*color)
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.get_or_add_rFonts()
    for attr in ('w:ascii', 'w:hAnsi', 'w:eastAsia', 'w:cs'):
        rFonts.set(qn(attr), name)


def set_cell_bg(cell, color):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:fill'), color)
    tcPr.append(shd)


def set_cell_margins(cell, top=80, bottom=80, left=100, right=100):
    tcPr = cell._tc.get_or_add_tcPr()
    mar = OxmlElement('w:tcMar')
    for tag, val in (('top', top), ('bottom', bottom), ('left', left), ('right', right)):
        el = OxmlElement('w:' + tag)
        el.set(qn('w:w'), str(val))
        el.set(qn('w:type'), 'dxa')
        mar.append(el)
    tcPr.append(mar)


def body(text, indent=True, align=WD_ALIGN_PARAGRAPH.LEFT, space_after=6):
    p = doc.add_paragraph()
    p.alignment = align
    pf = p.paragraph_format
    pf.line_spacing = 1.25
    pf.space_after = Pt(4)
    if indent:
        pf.first_line_indent = Cm(1.0)
    r = p.add_run(text)
    set_font(r)
    return p


def bullet(text, level=0):
    p = doc.add_paragraph(style='List Bullet')
    p.paragraph_format.left_indent = Cm(0.8 + level * 0.7)
    p.paragraph_format.first_line_indent = Cm(-0.45)
    p.paragraph_format.line_spacing = 1.35
    p.paragraph_format.space_after = Pt(3)
    r = p.add_run(text)
    set_font(r, 14.5)
    return p


def h1(text):
    p = doc.add_heading(level=1)
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(8)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    set_font(r, 18.5, True, color=DARK)
    return p


def h2(text):
    p = doc.add_heading(level=2)
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(5)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    set_font(r, 16, True, color=DARK)
    return p


def h3(text):
    p = doc.add_heading(level=3)
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    set_font(r, 15, True, color=DARK)
    return p


def figure(path, caption, width=Cm(16.2)):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.keep_with_next = True
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(2)
    p.add_run().add_picture(path, width=width)
    c = doc.add_paragraph()
    c.alignment = WD_ALIGN_PARAGRAPH.CENTER
    c.paragraph_format.space_after = Pt(8)
    r = c.add_run(caption)
    set_font(r, 13, name=FONT, color=GRAY)


def table(headers, rows, widths=None, font_size=11.0):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = 'Table Grid'
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    hdr = t.rows[0].cells
    for i, h in enumerate(headers):
        hdr[i].text = ''
        p = hdr[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(h)
        set_font(r, font_size, True, color=(255, 255, 255))
        set_cell_bg(hdr[i], '1F4E79')
        set_cell_margins(hdr[i])
        hdr[i].vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    trPr = t.rows[0]._tr.get_or_add_trPr()
    th = OxmlElement('w:tblHeader')
    th.set(qn('w:val'), 'true')
    trPr.append(th)
    for row in rows:
        cells = t.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = ''
            p = cells[i].paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            p.paragraph_format.line_spacing = 1.2
            p.paragraph_format.space_after = Pt(2)
            r = p.add_run(str(val))
            set_font(r, font_size)
            set_cell_margins(cells[i])
            cells[i].vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = w
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return t


def code(lines, caption=None):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.8)
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.0
    pPr = p._p.get_or_add_pPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:fill'), 'F2F2F2')
    pPr.append(shd)
    r = p.add_run('\n'.join(lines))
    set_font(r, 10.5, name='Consolas', color=(35, 35, 35))
    if caption:
        c = doc.add_paragraph()
        c.paragraph_format.space_after = Pt(8)
        rr = c.add_run(caption)
        set_font(rr, 11, color=GRAY)


def add_page_field(par):
    run = par.add_run()
    fld1 = OxmlElement('w:fldChar')
    fld1.set(qn('w:fldCharType'), 'begin')
    instr = OxmlElement('w:instrText')
    instr.set(qn('xml:space'), 'preserve')
    instr.text = ' PAGE '
    fld2 = OxmlElement('w:fldChar')
    fld2.set(qn('w:fldCharType'), 'separate')
    txt = OxmlElement('w:t')
    txt.text = '1'
    fld3 = OxmlElement('w:fldChar')
    fld3.set(qn('w:fldCharType'), 'end')
    run._r.append(fld1)
    run._r.append(instr)
    run._r.append(fld2)
    run._r.append(txt)
    run._r.append(fld3)
    set_font(run, 10, color=GRAY)


def toc_line(key, text, level=0):
    p = doc.add_paragraph()
    p.paragraph_format.line_spacing = 1.15
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.left_indent = Cm(0.0 if level == 0 else 0.7)
    p.paragraph_format.tab_stops.add_tab_stop(Cm(15.4), WD_TAB_ALIGNMENT.RIGHT, WD_TAB_LEADER.DOTS)
    r = p.add_run(text)
    set_font(r, 14 if level == 0 else 13.5, bold=(level == 0))
    r2 = p.add_run('\t' + PAGE_MAP.get(key, '00'))
    set_font(r2, 14 if level == 0 else 13.5, bold=(level == 0))


doc = Document()
sec = doc.sections[0]
sec.page_width = Cm(21.0)
sec.page_height = Cm(29.7)
sec.top_margin = Cm(2.3)
sec.bottom_margin = Cm(2.2)
sec.left_margin = Cm(2.6)
sec.right_margin = Cm(2.2)
sec.header_distance = Cm(1.0)
sec.footer_distance = Cm(1.0)
sec.different_first_page_header_footer = True

st = doc.styles['Normal']
st.font.name = FONT
st.font.size = Pt(BODY)
rPr = st.element.get_or_add_rPr()
rFonts = rPr.get_or_add_rFonts()
for a in ('w:ascii', 'w:hAnsi', 'w:eastAsia', 'w:cs'):
    rFonts.set(qn(a), FONT)
st.paragraph_format.line_spacing = 1.5
st.paragraph_format.space_after = Pt(6)
st.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT

hp = sec.header.paragraphs[0]
hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
hr = hp.add_run('Master of Reins  |  รายงานโครงงาน')
set_font(hr, 9.5, color=GRAY)
fp = sec.footer.paragraphs[0]
fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
fr = fp.add_run('หน้า ')
set_font(fr, 10, color=GRAY)
add_page_field(fp)

# Cover placeholder
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(8)
logo_table = doc.add_table(rows=1, cols=1)
logo_table.alignment = WD_TABLE_ALIGNMENT.CENTER
logo_table.style = 'Table Grid'
cell = logo_table.rows[0].cells[0]
cell.width = Cm(8.5)
logo_table.rows[0].height = Cm(3.2)
cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
cp = cell.paragraphs[0]
cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
rr = cp.add_run('[ วางโลโก้มหาวิทยาลัย ]')
set_font(rr, 14, color=GRAY)
set_cell_bg(cell, 'F7FAFF')

for _ in range(1):
    doc.add_paragraph()
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run('รายงานโครงงาน')
set_font(r, 18.5, True, color=DARK)
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(10)
r = p.add_run('Master of Reins')
set_font(r, 34, True, color=DARK)
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run('เกมแข่งม้า 2 มิติ แนว Side-scrolling Racing')
set_font(r, 18, color=DARK)
doc.add_paragraph()

for line in ['จัดทำโดย  [ ชื่อ-นามสกุล ]', 'รหัสนักศึกษา  [ รหัสนักศึกษา ]',
             'สาขา/คณะ  [ สาขาวิชา / คณะ ]', 'อาจารย์ที่ปรึกษา  [ ชื่ออาจารย์ที่ปรึกษา ]',
             'ภาคเรียนที่  [ ภาคเรียน ]  ปีการศึกษา  [ ปีการศึกษา ]']:
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(5)
    r = p.add_run(line)
    set_font(r, 16)
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run('[ ชื่อมหาวิทยาลัย / คณะ ]')
set_font(r, 16, True)
doc.add_page_break()

# TOC
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(14)
r = p.add_run('สารบัญ')
set_font(r, 24, True, color=DARK)

toc_line('ch1', 'บทที่ 1  บทนำ', 0)
toc_line('s11', '1.1  ที่มาและความสำคัญของโปรเจ็ค', 1)
toc_line('s12', '1.2  ประเภทของโครงการ', 1)
toc_line('s13', '1.3  ประโยชน์', 1)
toc_line('s14', '1.4  ขอบเขตของโครงการ', 1)
toc_line('ch2', 'บทที่ 2  ส่วนการพัฒนา', 0)
toc_line('s21', '2.1  เนื้อเรื่องย่อและวิธีการเล่น', 1)
toc_line('s22', '2.2  คลาสไดอะแกรมและคำอธิบาย', 1)
toc_line('s23', '2.3  รูปแบบการพัฒนา Application', 1)
toc_line('s24', '2.4  การประยุกต์ใช้แนวคิด OOP', 1)
toc_line('s25', '2.5  หน้าจอ GUI และส่วนประกอบ', 1)
toc_line('s26', '2.6  Event Handling', 1)
toc_line('s27', '2.7  อัลกอริทึมที่สำคัญ', 1)
toc_line('ch3', 'บทที่ 3  สรุป', 0)
toc_line('s31', '3.1  ปัญหาที่พบระหว่างการพัฒนา', 1)
toc_line('s32', '3.2  จุดเด่นของโปรแกรม', 1)
toc_line('ref', 'เอกสารอ้างอิง', 0)
toc_line('app', 'ภาคผนวก ก  แนวทางการติดตั้งและรันโปรแกรม', 0)
doc.add_page_break()

# Chapter 1
h1('บทที่ 1  บทนำ')
h2('1.1  ที่มาและความสำคัญของโปรเจ็ค')
body('การพัฒนาเกมเป็นงานที่รวมองค์ความรู้หลายด้านไว้ด้วยกัน ทั้งการออกแบบประสบการณ์ผู้เล่น การเขียนโปรแกรมเชิงวัตถุ การจัดการข้อมูลแผนที่ การคำนวณการชน (Collision) การออกแบบส่วนติดต่อผู้ใช้ และการทดสอบระบบภายใต้เงื่อนไขการใช้งานจริง โครงการ “Master of Reins” จึงถูกพัฒนาขึ้นในลักษณะเกมแข่งม้าแบบสองมิติที่ผู้เล่นวิ่งจากซ้ายไปขวา แข่งกับบอทห้าตัว ผ่านจุดตรวจและเส้นชัย โดยมีระบบพลัง stamina การเร่งความเร็ว ไอเทม การต่อสู้ และเส้นทางทางเลือกที่ทำให้การแข่งขันไม่ได้ขึ้นอยู่กับความเร็วเพียงอย่างเดียว')
body('เกมนี้พัฒนาด้วยภาษา Java และใช้ Java Swing/AWT สำหรับหน้าจอและกราฟิกสองมิติ โดยไม่ใช้ game engine สำเร็จรูป ผู้พัฒนาต้องออกแบบวงจรเกม (Game Loop) ระบบโหลดแผนที่จาก Tiled ระบบชนแบบ swept collision ระบบ AI ของบอท และระบบแสดงผลเองทั้งหมด จึงเป็นโครงการที่แสดงให้เห็นการนำหลักการเขียนโปรแกรมเชิงวัตถุและอัลกอริทึมไปใช้กับงานที่มีปฏิสัมพันธ์แบบทันที (Real-time Interactive Application)')
h2('1.2  ประเภทของโครงการ')
body('โครงการนี้จัดเป็นเกมคอมพิวเตอร์ประเภท Desktop Game หรือ Standalone Application ที่ทำงานบนเครื่องคอมพิวเตอร์ส่วนบุคคล ใช้รูปแบบการเล่นแบบ Side-scrolling Racing/Action ผสมกับการแข่งขันกับ AI โดยผู้เล่นหนึ่งคนแข่งขันกับบอทห้าตัวบนแผนที่เดียวที่มีจุดเริ่มต้น จุดตรวจสองจุด และเส้นชัย')
body('ลักษณะสำคัญของโครงการมีดังนี้')
bullet('เป็นเกมสองมิติที่ใช้มุมมองด้านข้าง (Side View) และเลื่อนฉากตามผู้เล่นจากซ้ายไปขวา')
bullet('เป็นเกมผู้เล่นคนเดียว (Single-player) ที่มีบอทควบคุมด้วยคอมพิวเตอร์ ห้าตัว')
bullet('เป็นเกมแข่งความเร็วที่มีระบบ stamina การเร่งความเร็ว และการจัดการทรัพยากรระหว่างการแข่งขัน')
bullet('มีระบบต่อสู้ การป้องกัน โล่ และไอเทมเสริม ทำให้การแข่งขันมีมิติของการตัดสินใจมากกว่าการกดวิ่งอย่างเดียว')
bullet('เป็นโครงการเชิงการศึกษา (Educational Project) ที่มุ่งแสดงการประยุกต์ใช้ Java, OOP, อัลกอริทึม และการออกแบบส่วนติดต่อผู้ใช้')
body('โปรแกรมไม่ได้อยู่ในรูปแบบ Java Applet และไม่ได้ทำงานผ่านเว็บเบราว์เซอร์ แต่เป็นแอปพลิเคชัน Java SE ที่เปิดหน้าต่างด้วย JFrame และเรียกใช้ผ่านไฟล์ JAR หรือคำสั่ง java -jar')
h2('1.3  ประโยชน์')
body('ประโยชน์ของโครงการสามารถแบ่งได้เป็นสามด้าน คือ ประโยชน์ต่อผู้พัฒนา ประโยชน์ต่อผู้เล่น และประโยชน์ในเชิงการศึกษา')
h3('ประโยชน์ต่อผู้พัฒนา')
bullet('ได้ฝึกออกแบบสถาปัตยกรรมโปรแกรมโดยแบ่งเป็นแพ็กเกจ app, world, racer, collision, items, combat และ bot อย่างชัดเจน')
bullet('ได้ใช้หลักการเขียนโปรแกรมเชิงวัตถุ ทั้ง encapsulation, composition, inheritance, polymorphism และ abstraction ในงานจริง')
bullet('ได้ฝึกอัลกอริทึมสำคัญ เช่น การค้นหาเส้นทางด้วย Dijkstra และ A*, การตรวจการชนแบบ swept collision และการคำนวณอันดับการแข่งขัน')
bullet('ได้ฝึกอ่านและตรวจสอบข้อมูลจากไฟล์ Tiled รวมถึงจัดการเส้นทางของไฟล์ภาพและข้อผิดพลาดของข้อมูล')
h3('ประโยชน์ต่อผู้เล่น')
body('ผู้เล่นได้รับประสบการณ์การแข่งขันที่ต้องบริหาร stamina เลือกจังหวะเร่ง เก็บไอเทม หลบอุปสรรค และตัดสินใจว่าจะใช้เส้นทางหลักหรือเส้นทางลัด ผู้เล่นยังสามารถใช้การโจมตี การป้องกัน และการชาร์จพลังเพื่อสร้างความได้เปรียบหรือแก้ไขสถานการณ์ได้')
h3('ประโยชน์ในเชิงการศึกษา')
body('โครงการนี้สามารถใช้เป็นตัวอย่างการเชื่อมโยงเนื้อหาหลายบทเข้าด้วยกัน ตั้งแต่การเขียนโปรแกรมเชิงวัตถุ โครงสร้างข้อมูล กราฟและการค้นหาเส้นทาง ไปจนถึงการออกแบบส่วนติดต่อผู้ใช้แบบ Event-driven และการจัดทำเอกสารประกอบโครงการ')
h2('1.4  ขอบเขตของโครงการ')
body('ขอบเขตของโครงการกำหนดให้เกมทำงานบนแผนที่หลักหนึ่งแผนที่ คือ assets/Map/Background.tmx ซึ่งเป็นแผนที่แบบ Orthogonal ขนาด 900 × 200 tiles ขนาด tile 16 × 16 พิกเซล มีเลเยอร์ภาพหลายชั้น เลเยอร์ collision สำหรับพื้นที่ทึบ เลเยอร์ wet สำหรับพื้นที่น้ำ และเลเยอร์ race สำหรับจุดเกิด จุดตรวจ เส้นชัย และ waypoint ของบอท')
body('ขอบเขตการทำงานที่พัฒนาเสร็จในโครงการมีดังนี้')
bullet('ผู้เล่นหนึ่งคนแข่งขันกับบอทห้าตัว โดยทุกตัวใช้กฎการเคลื่อนที่ stamina และการชนชุดเดียวกัน')
bullet('มีจุดตรวจสองจุดและเส้นชัยหนึ่งจุด ผู้เล่นและบอทต้องผ่านจุดตรวจตามลำดับก่อนเข้าเส้นชัย')
bullet('มีระบบ stamina และการเร่งความเร็วด้วยการกด Space สองครั้งภายในเวลาที่กำหนด')
bullet('มีไอเทม stamina, speed และ shield รวมถึงพื้นที่น้ำที่ลดความเร็ว')
bullet('มีระบบโจมตี ป้องกัน โล่ สตั๊น และสโลว์จากการต่อสู้')
bullet('มีระบบชาร์จพลังด้วยการกด O ค้าง เพื่อรับ stamina เมื่อชาร์จสำเร็จ')
bullet('มีบอทที่ใช้กราฟ waypoint การค้นหาเส้นทาง และบุคลิกภาพที่แตกต่างกัน')
bullet('มีหน้าจอ HUD แสดง stamina, สถานะ sprint, ความเร็วปัจจุบัน และข้อความแจ้งเหตุการณ์')
body('สิ่งที่อยู่นอกขอบเขตของโครงการ ได้แก่ ระบบหลายแผนที่หรือหลายด่าน ระบบผู้เล่นหลายคนผ่านเครือข่าย ระบบบันทึกและโหลดเกม ระบบเสียง ระบบแก้ไขแผนที่ภายในเกม การรองรับอุปกรณ์เคลื่อนที่ และการฝังไฟล์ภาพทั้งหมดไว้ใน JAR โดยตรง')
body('รายละเอียดขอบเขตและข้อเสนอโครงการฉบับเต็มให้แนบตามภาคผนวก ก ซึ่งเป็นส่วนที่นักศึกษาสามารถแทนที่ด้วย Proposal ฉบับจริงของรายวิชาได้')

# Chapter 2
doc.add_page_break()
h1('บทที่ 2  ส่วนการพัฒนา')
h2('2.1  เนื้อเรื่องย่อและวิธีการเล่น')
body('เรื่องราวของเกมเกิดขึ้นในฉากฤดูหนาวแบบแฟนตาซี ผู้เล่นรับบทเป็นนักขี่ม้าที่ต้องแข่งขันกับคู่แข่งห้าคนเพื่อไปให้ถึงเส้นชัยให้เร็วที่สุด เส้นทางแข่งขันมีทั้งเส้นทางหลักที่คดเคี้ยวและเส้นทางอ้อมที่ลึกกว่าเดิม ผู้เล่นต้องบริหาร stamina ให้เพียงพอต่อการเร่ง หลบสิ่งกีดขวาง เก็บไอเทม และตัดสินใจว่าจะเลือกเส้นทางใด')
body('การเล่นเริ่มจากหน้าจอเมนู ผู้เล่นกดปุ่ม Marathon Race เพื่อเริ่มการแข่งขัน เกมจะนับถอยหลัง 3, 2, 1 แล้วเข้าสู่สถานะ RACING ผู้เล่นต้องวิ่งไปทางขวา ผ่านจุดตรวจทั้งสองจุดตามลำดับ และแตะเส้นชัย ระหว่างทางสามารถเก็บไอเทม stamina เพื่อเติมพลัง เก็บไอเทม speed เพื่อเพิ่มความเร็วชั่วคราว และเก็บไอเทม shield เพื่อป้องกันการโจมตีหรือการชน')
body('การควบคุมหลักแสดงในตารางที่ 1')
table(['ปุ่ม', 'การทำงาน'],
      [['Space', 'วิ่งไปทางขวา; กดซ้ำภายใน 0.3 วินาทีเพื่อเริ่ม sprint'],
       ['W / S หรือ ↑ / ↓', 'ขยับขึ้น / ลง'],
       ['O (กดค้าง)', 'ชาร์จพลังเพื่อรับ stamina เมื่อชาร์จครบตามเงื่อนไข'],
       ['J', 'โจมตีคู่แข่งในระยะ'],
       ['K (กดค้าง)', 'ป้องกันตัว ใช้ stamina ต่อเนื่อง'],
       ['P หรือ Esc', 'หยุดชั่วคราว / กลับสู่เกม'],
       ['F3', 'เปิด-ปิดกรอบตรวจสอบ collision และพื้นที่ต่าง ๆ']],
      widths=[Cm(4.2), Cm(11.3)])
body('ลำดับสถานะของเกมคือ MENU → COUNTDOWN → RACING → FINISHED โดยในสถานะ COUNTDOWN ผู้เล่นยังเคลื่อนที่ไม่ได้ แต่สามารถกดปุ่มค้างไว้เพื่อให้เริ่มทำงานทันทีเมื่อถึงจังหวะ GO ระบบจะเก็บค่าปุ่มที่กดค้างไว้และไม่ล้างปุ่มที่ผู้เล่นยังกดอยู่เมื่อเริ่มการแข่งขัน')
body('องค์ประกอบการเล่นที่สำคัญมีดังนี้')
bullet('Stamina ใช้สำหรับ sprint และการป้องกันตัว เมื่อ stamina หมด ผู้เล่นจะไม่สามารถ sprint ได้จนกว่าจะฟื้นหรือเก็บไอเทม')
bullet('การชนพื้นที่ทึบหรือขอบโลกขณะ sprint จะทำให้ติดสตั๊นและสโลว์ ส่วนการชนขณะวิ่งปกติจะทำให้สโลว์โดยไม่สตั๊น')
bullet('น้ำจะลดความเร็วลงเหลือ 80% และมีผลกับทั้งผู้เล่นและบอท')
bullet('ไอเทม stamina จะเกิดใหม่ตามเวลาที่กำหนด ส่วน speed และ shield มีระยะเวลาหรือจำนวนครั้งตามที่กำหนด')

h2('2.2  คลาสไดอะแกรมและคำอธิบาย')
body('สถาปัตยกรรมของเกมแบ่งออกเป็นแพ็กเกจตามความรับผิดชอบ เพื่อลดการพึ่งพากันระหว่างส่วนภาพ ส่วนข้อมูลเกม และส่วนกฎกติกา แพ็กเกจ app ทำหน้าที่เป็นจุดเริ่มต้นและจัดการหน้าต่างเกม แพ็กเกจ world เก็บข้อมูลแผนที่และการแข่งขัน แพ็กเกจ racer เก็บสถานะและการเคลื่อนที่ของผู้แข่งขัน แพ็กเกจ collision ตรวจการชน แพ็กเกจ items เก็บไอเทมและแอนิเมชัน แพ็กเกจ combat จัดการการต่อสู้ และแพ็กเกจ bot จัดการ AI')
figure('report-assets/class-diagram.png', 'รูปที่ 1  คลาสไดอะแกรมหลักของเกม (แบบย่อ)', Cm(15.8))
body('รูปที่ 1 แสดงคลาสหลักและความสัมพันธ์แบบ owns/uses/implements โดย RaceWorld เป็นศูนย์กลางของสถานะการแข่งขัน มี RaceTrack, Racer, CollisionSystem, TerrainEffects, StaminaPickup และ BotBrain อยู่ภายใน Racer มี Stamina, RacerStatus, CombatState และ ChargeState เป็นองค์ประกอบย่อย ส่วน GamePanel ทำหน้าที่แสดงผลแผนที่ ผู้แข่งขัน ต้นไม้ และ HUD')
body('ตารางที่ 2 สรุปหน้าที่ของคลาสสำคัญในแต่ละแพ็กเกจ')
table(['แพ็กเกจ', 'คลาสสำคัญ', 'หน้าที่หลัก'],
      [['game.app', 'Main, GamePanel, Camera, InputState, SpacebarControl', 'เริ่มโปรแกรม แสดงหน้าจอ รับอินพุต จัดการกล้อง และวาด HUD'],
       ['game.world', 'TiledMap, RaceWorld, RaceTrack, RaceProgress, TerrainEffects, TreeSprite', 'โหลดแผนที่และชน เก็บสถานะการแข่งขัน จุดตรวจ เส้นชัย น้ำ และต้นไม้'],
       ['game.racer', 'Racer, RacerAction, RacerStatus, Stamina, ChargeState', 'เก็บสถานะผู้แข่งขัน การเคลื่อนที่ พลัง stamina สถานะสตั๊น/สโลว์ และการชาร์จ'],
       ['game.collision', 'CollisionSystem, CollisionShape, CollisionMapLoader', 'อ่านพื้นที่ชนจาก Tiled ตรวจการชนแบบ swept และเลื่อนไปตามขอบ'],
       ['game.items', 'StaminaPickup, SpriteAnimation, SweptTrigger', 'เก็บไอเทม แอนิเมชันการเกิดใหม่ และตรวจการเก็บไอเทม'],
       ['game.combat', 'CombatSystem, CombatState, CombatAction, CombatRenderer', 'จัดการการโจมตี การป้องกัน โล่ ระยะเวลา และการแสดงผลการต่อสู้'],
       ['game.bot', 'BotBrain, BotController, BotRouteGraph, BotNavigation, BotPersonality', 'สร้างเส้นทาง ค้นหาเส้นทาง เลือกพฤติกรรม และควบคุมบอท']],
      widths=[Cm(3.0), Cm(6.5), Cm(6.0)], font_size=11.5)
body('RaceWorld ทำหน้าที่ประสานงานเกือบทั้งหมด เช่น เริ่มการแข่งขัน นับถอยหลัง อัปเดตผู้แข่งขันแต่ละตัว ตรวจการชน เก็บไอเทม ตรวจจุดตรวจ และจัดอันดับ RaceTrack เก็บจุดเกิด จุดตรวจ เส้นชัย และเส้นทางของบอท ส่วน RaceProgress แยกความคืบหน้าของผู้แข่งขันแต่ละคน เพื่อให้บอทและผู้เล่นมีสถานะการผ่านจุดตรวจของตนเอง')
body('TiledMap อ่านไฟล์ TMX และ TSX โดยตรง ประกอบภาพกระเบื้องและต้นไม้จากไฟล์ PNG ภายนอก และเก็บข้อมูล collision จาก Object Layer ชื่อ collision ส่วน CollisionMapLoader แปลงรูปทรงที่รองรับ ได้แก่ รูปสี่เหลี่ยม รูปหลายเหลี่ยม และรูปวงรี ให้เป็น CollisionShape ในพิกัดโลก')

h2('2.3  รูปแบบการพัฒนา Application')
body('เกมนี้เป็น Java Application แบบ Desktop ไม่ใช่ Applet และไม่ได้ทำงานบนเว็บเซิร์ฟเวอร์ โดยมี Main เป็นจุดเริ่มต้นในการสร้าง JFrame แล้วใส่ GamePanel เป็น content pane จากนั้น GamePanel จะโหลดแผนที่ สร้าง RaceWorld และเริ่ม Swing Timer สำหรับอัปเดตเกมทุกประมาณ 16 มิลลิวินาที')
body('เทคโนโลยีที่ใช้มีดังนี้')
bullet('ภาษา Java ระดับ Java SE 17 ขึ้นไป ใช้ record, switch expression, text block และ pattern matching ตามความเหมาะสม')
bullet('Java Swing/AWT สำหรับหน้าต่าง ปุ่ม การวาดภาพสองมิติ และการจัดการ event')
bullet('Tiled Map Editor สำหรับสร้างแผนที่แบบ Orthogonal, ใช้ Tile Layer แบบ CSV และ Object Layer สำหรับ collision/race/wet')
bullet('ไฟล์ภาพ PNG และไฟล์ TSX/TMX ภายนอกที่โหลดผ่าน ImageIO และ Java XML DOM')
bullet('ไม่ใช้ไลบรารีเกมหรือไลบรารีกราฟิกภายนอก ทำให้โปรแกรมมี dependencies น้อยและสามารถรันด้วยไฟล์ JAR')
body('โครงสร้างการทำงานแบ่งเป็นชั้น Presentation (GamePanel/Main), Domain (RaceWorld/TiledMap), Entity (Racer), System (Collision/Combat/Items) และ AI (BotBrain/BotRoute) การแบ่งชั้นนี้ทำให้การแก้ไขระบบหนึ่งไม่กระทบส่วนอื่นมากนัก เช่น การเปลี่ยนสูตรการชนทำได้ใน CollisionSystem โดยไม่ต้องแก้ GamePanel')
body('การจัดส่งโปรแกรมทำได้สองรูปแบบ คือ รันจาก IDE ในโปรเจกต์ หรือสร้างไฟล์ JAR แล้วรันด้วยคำสั่ง java -jar MasterOfReins.jar โดยต้องวางโฟลเดอร์ assets และ asset-packs ไว้ในตำแหน่งเดียวกับ JAR เพราะโค้ดโหลดภาพและแผนที่จากไฟล์ภายนอก ไม่ได้ฝังไว้ใน classpath')

h2('2.4  การประยุกต์ใช้แนวคิด OOP')
h3('2.4.1  Constructor')
body('Constructor ถูกใช้เพื่อกำหนดสถานะเริ่มต้นของวัตถุและตรวจสอบข้อมูลที่จำเป็น ตัวอย่างเช่น Racer รับตำแหน่ง ขนาด และ Stamina ใน constructor เพื่อให้ทุกตัวมีพลังและกรอบการเคลื่อนที่ครบก่อนเริ่มเกม')
code(['public Racer(int x, int y, int width, int height, Stamina stamina) {',
      '    this.x = x; this.y = y;',
      '    this.width = width; this.height = height;',
      '    this.stamina = stamina;',
      '}'], 'ตัวอย่าง constructor ของ Racer')
body('GamePanel มี constructor ที่โหลดแผนที่ สร้าง RaceWorld โหลดภาพตัวละครและแอนิเมชัน แล้วตั้งค่า Timer สำหรับเกมลูป หากโหลดแผนที่ไม่ได้ จะเก็บข้อผิดพลาดไว้และแสดงข้อความแทนการ crash ทั้งโปรแกรม ส่วน RaceWorld constructor ตรวจสอบว่า track ไม่เป็น null และสร้างผู้เล่น บอท และรายการไอเทมก่อนเริ่มเกม')
body('นอกจาก constructor แบบปกติแล้ว โปรเจกต์ยังใช้ compact constructor ของ record ในการตรวจสอบค่า เช่น StaminaPickup.Definition ตรวจว่า image, type, opacity, respawnSeconds และค่าพลังงานมีความถูกต้อง หากค่าไม่ถูกต้องจะโยน IllegalArgumentException ทันที ทำให้ข้อผิดพลาดถูกพบตั้งแต่ตอนโหลดข้อมูล')
h3('2.4.2  Encapsulation')
body('Encapsulation คือการซ่อนสถานะภายในและเปิดเฉพาะ method ที่จำเป็น โปรเจกต์ใช้ private field เป็นหลัก เช่น Racer เก็บ velocityX, velocityY, stunRemaining และ slowRemaining ไว้ภายใน ไม่ให้คลาสอื่นแก้ไขโดยตรง คลาสอื่นอ่านค่าผ่าน getter เช่น getX(), getY(), getFeetBounds(), isStunned() และ getSpeed()')
body('CollisionShape เก็บ Path2D, Area ที่ขยายแล้ว และรายการ edge ไว้ภายใน เพื่อให้การตรวจการชนใช้ข้อมูลที่คำนวณไว้ซ้ำได้อย่างมีประสิทธิภาพ ส่วน RaceProgress เก็บจำนวนจุดตรวจที่ผ่านและเวลาเข้าเส้นชัย โดยเปิด method check() และ getPassed() ให้ RaceWorld เรียกใช้')
h3('2.4.3  Composition')
body('Composition ปรากฏชัดในความสัมพันธ์แบบ “มีอยู่ภายใน” ตัวอย่างเช่น GamePanel มี TiledMap, RaceWorld, Camera, InputState และ SpacebarControl เป็นองค์ประกอบ RaceWorld มี RaceTrack, รายการ Racer, CollisionSystem, TerrainEffects, รายการ StaminaPickup, CombatSystem และ BotBrain')
body('Racer ประกอบด้วย Stamina, RacerStatus, CombatState และ ChargeState ซึ่งแยกความรับผิดชอบด้านพลัง สถานะการต่อสู้ และการชาร์จออกจากกัน TiledMap ประกอบด้วย TileSet, TileLayer และ TreeSprite หลายตัว ส่วน BotBrain ประกอบด้วย BotController, BotRouteGraph.Plan และพฤติกรรมต่าง ๆ เช่น BotItemBehavior, BotPassingBehavior และ BotChargeBehavior')
h3('2.4.4  Polymorphism')
body('Polymorphism ถูกใช้ผ่าน interface RacerController ซึ่งมี method createAction(InputState, Racer, double) ให้ PlayerController และ BotController implement ความแตกต่างคือ PlayerController อ่านค่าจาก InputState ส่วน BotController อ่านเส้นทางและตัดสินใจทิศทางเอง RaceWorld เรียกใช้ผ่านตัวแปรประเภท RacerController โดยไม่ต้องรู้ว่าเป็นผู้เล่นหรือบอท')
code(['public interface RacerController {',
      '    RacerAction createAction(InputState input, Racer racer, double seconds);',
      '}',
      '// RaceWorld เรียกใช้ผ่าน interface',
      'RacerAction action = controller.createAction(input, racer, step);'],
      'ตัวอย่าง polymorphism ผ่าน RacerController')
body('นอกจากนี้ GamePanel ยังมี interface ภายใน Renderable ซึ่งมี RacerRenderable และ TreeRenderable เป็น implementation ทำให้สามารถเรียงลำดับการวาดผู้แข่งขันและต้นไม้ตามค่า groundY ได้ด้วยคอลเลกชันเดียว')
body('Polymorphism ยังเกิดจากการ override method ของ Swing เช่น RoundedButton, PauseButton และ RoundedPanel override paintComponent() เพื่อวาดปุ่มและ面板แบบกำหนดเอง ขณะที่ GamePanel override paintComponent() เพื่อวาดฉากเกมทั้งหมด')
h3('2.4.5  Abstraction')
body('โปรเจกต์ใช้ Abstraction ผ่าน interface เป็นหลัก ได้แก่ RacerController และ Renderable ซึ่งกำหนดสัญญาของพฤติกรรมโดยไม่ผูกกับรายละเอียดการทำงาน ผู้เรียกใช้รู้เพียงว่าต้องส่ง InputState, Racer และเวลาเข้าไป แล้วจะได้ RacerAction กลับมา จึงสามารถเปลี่ยนหรือเพิ่ม controller ได้โดยไม่ต้องแก้ RaceWorld')
h3('2.4.6  Inheritance')
body('Inheritance ถูกใช้กับส่วนติดต่อผู้ใช้ของ Swing อย่างชัดเจน GamePanel extends JPanel เพื่อให้เป็น面板ที่วาดฉากเกมได้ RoundedButton extends JButton เพื่อเพิ่มรูปทรงปุ่มแบบมน PauseButton extends RoundedButton เพื่อเพิ่มไอคอนหยุดชั่วคราว และ RoundedPanel extends JPanel เพื่อวาดพื้นหลัง面板แบบมน')
code(['public final class GamePanel extends JPanel { ... }',
      'private static class RoundedButton extends JButton { ... }',
      'private static final class PauseButton extends RoundedButton { ... }'],
      'ตัวอย่าง inheritance ในส่วน GUI')
body('คลาสลูกสามารถเรียก super.paintComponent() เพื่อให้ Swing วาดองค์ประกอบพื้นฐานก่อน แล้วจึงวาดรายละเอียดเพิ่มเติม การสืบทอดช่วยลดโค้ดซ้ำและทำให้พฤติกรรมร่วมของปุ่มถูกกำหนดไว้ที่คลาสฐาน')

h2('2.5  หน้าจอ GUI และส่วนประกอบ')
body('หน้าจอเกมเป็นหน้าต่าง Java Swing ขนาดเต็มจอ (MAXIMIZED_BOTH) ภายในมี GamePanel ทำหน้าที่วาดฉากเกมด้วย Graphics2D และมี Swing component สำหรับปุ่มและเมนูต่าง ๆ โครงสร้าง GUI แสดงในรูปที่ 2')
figure('report-assets/gui-structure.png', 'รูปที่ 2  โครงสร้าง GUI และเส้นทางการเกิด event', Cm(14.8))
body('ส่วนประกอบหลักของ GUI มีดังนี้')
table(['Component', 'ประเภท', 'หน้าที่'],
      [['JFrame', 'Swing window', 'หน้าต่างหลัก ตั้งชื่อเกมและปิดโปรแกรมเมื่อปิดหน้าต่าง'],
       ['GamePanel', 'JPanel', 'วาดแผนที่ ผู้เล่น บอท ต้นไม้ HUD และจัดการ input'],
       ['stageMenu', 'JPanel (GridLayout)', 'เมนูเริ่มเกม ประกอบด้วย Marathon Race และ Flat Sprint Race'],
       ['marathonButton', 'RoundedButton', 'เริ่มการแข่งขันโหมด marathon'],
       ['sprintRaceButton', 'RoundedButton', 'ปุ่มโหมด Flat Sprint (ยังไม่เปิดใช้ในแผนที่นี้)'],
       ['pauseButton', 'PauseButton', 'ปุ่มหยุดชั่วคราวที่มุมขวาบน'],
       ['pauseMenu', 'RoundedPanel (GridLayout)', 'เมนู PAUSED พร้อม Resume และ Quit Game'],
       ['finishMenu', 'RoundedPanel (FlowLayout)', 'เมนูหลังจบเกม พร้อม Race Again และ Quit Game'],
       ['HUD', 'Graphics2D', 'แสดง stamina, SPRINT ON/OFF, SPEED และข้อความแจ้งเหตุการณ์']],
      widths=[Cm(3.4), Cm(5.4), Cm(6.8)], font_size=11.5)
body('ตารางที่ 3 สรุปส่วนประกอบหลักของ GUI และหน้าที่ของแต่ละองค์ประกอบ')
body('HUD ถูกวาดด้วย Graphics2D ไม่ได้ใช้ JLabel ทำให้ควบคุมตำแหน่ง ขนาด และสีได้อิสระ โดยแสดงแถบ stamina ข้อความ SPRINT ON/OFF และค่าความเร็วปัจจุบันเป็นตัวเลขบน panel มุมขวาล่าง')
figure('report-assets/race.png', 'รูปที่ 3  หน้าจอการแข่งขันและ HUD มุมขวาล่าง', Cm(15.0))

h2('2.6  Event Handling')
body('เกมใช้ event handling สองกลุ่มหลัก คือ กลุ่มคีย์บอร์ดผ่าน InputMap/ActionMap ของ Swing และกลุ่มปุ่ม/หน้าต่างผ่าน ActionListener, ComponentListener และ FocusListener')
body('การผูกคีย์ใช้ WHEN_IN_FOCUSED_WINDOW ทำให้เกมยังรับคีย์แม้โฟกัสอยู่ใน component ลูก ตารางที่ 4 สรุป event ที่สำคัญ')
table(['Event / Component', 'การทำงาน'],
      [['Key pressed/released: W, S, UP, DOWN', 'ปรับ movingUp/movingDown ใน InputState'],
       ['Key pressed/released: SPACE', 'เริ่ม/หยุดวิ่งไปทางขวา และตรวจการกดซ้ำเพื่อ sprint ผ่าน SpacebarControl'],
       ['Key pressed/released: O', 'เริ่ม/หยุดสถานะ charging สำหรับการชาร์จพลัง'],
       ['Key pressed: P หรือ Esc', 'หยุดชั่วคราวหรือกลับสู่เกม'],
       ['Key released: F3', 'สลับการแสดงกรอบ collision'],
       ['ActionListener ของปุ่ม', 'เริ่มแข่ง หยุดชั่วคราว กลับเมนู หรือแข่งใหม่'],
       ['Swing Timer 16 ms', 'เรียก updateGame() เพื่ออัปเดต RaceWorld และ repaint'],
       ['ComponentListener', 'จัดตำแหน่งปุ่มใหม่เมื่อหน้าต่างเปลี่ยนขนาด']],
      widths=[Cm(6.0), Cm(9.5)], font_size=11.8)
body('SpacebarControl แยกการกด Space ครั้งใหม่จาก key repeat และตรวจการกดซ้ำภายใน 300 มิลลิวินาทีเพื่อเริ่ม sprint ทำให้การกดค้างหนึ่งครั้งเป็นการวิ่งปกติ และการกดซ้ำเป็นการเร่งความเร็ว')
body('สำหรับการเริ่มการแข่งขันใหม่ ระบบจะเก็บปุ่มที่ผู้เล่นกดค้างไว้ เช่น S และ O เพื่อให้เริ่มทำงานต่อเนื่องเมื่อถึง GO แต่จะล้างเฉพาะ action ที่เป็น one-shot เช่น attack request เพื่อไม่ให้เกิดการโจมตีค้างจากเมนู')

h2('2.7  อัลกอริทึมที่สำคัญ')
h3('2.7.1  Game Loop และลำดับการอัปเดต')
body('RaceWorld.update() รับเวลาเป็นวินาทีและแบ่งการอัปเดตเป็นขั้นละไม่เกิน 1/60 วินาที เพื่อให้การเคลื่อนที่และการชนมีเสถียรภาพแม้เฟรมเรตของหน้าจอไม่คงที่ ในแต่ละขั้นจะอัปเดตไอเทม ตรวจสถานะผู้แข่งขัน สร้าง action จาก controller ตรวจการต่อสู้ เคลื่อนที่ผู้แข่งขัน ตรวจการชาร์จ เก็บไอเทม ตรวจจุดตรวจ/เส้นชัย และเพิ่มเวลาแข่งขัน')
h3('2.7.2  Collision Detection')
body('การชนใช้กรอบเท้าของผู้แข่งขัน (Feet Bounds) แทนกรอบภาพทั้งตัว เพื่อให้การชนดูสมจริงและไม่ติดภาพส่วนหัวหรือหางม้า กรอบเท้าถูกกวาดไปตามเส้นทางการเคลื่อนที่ของเฟรม (Swept Collision) แล้วตรวจกับพื้นที่ชนที่ถูกขยายด้วยขนาดครึ่งหนึ่งของกรอบเท้า (Minkowski expansion) ทำให้จุดศูนย์กลางเท้าเคลื่อนที่ไปชนขอบที่ขยายแทนการตรวจกรอบซ้อนกรอบ')
body('CollisionSystem.moveWithContact() วนแก้ไขการชนได้หลายรอบ เพื่อให้ผู้แข่งขันเลื่อนไปตามขอบกำแพง (Slide) แทนการหยุดทันที และคืนค่า MotionSegment สำหรับใช้ตรวจจุดตรวจ/เส้นชัยแบบ swept เช่นเดียวกับการชน')
code(['Movement m = collisions.moveWithContact(feet, dx, dy, centreLimits);',
      'x += m.dx(); y += m.dy();',
      '// m.hit() == true แปลว่ามีการชน และ m.path() เก็บช่วงการเคลื่อนที่'])
h3('2.7.3  จุดตรวจและอันดับ')
body('RaceTrack เก็บ Gate ของจุดตรวจและเส้นชัยเป็น Rectangle2D และใช้ SweptTrigger.entryFraction() เพื่อหาว่าการเคลื่อนที่ของเท้าเข้าสู่กรอบหรือไม่ แม้กรอบจะบางมากก็ไม่ถูกข้ามไป RaceProgress ตรวจจุดตรวจตามลำดับโดยใช้ passed เป็นตัวชี้ จึงป้องกันการข้ามจุดตรวจหรือการนับจุดตรวจผิดลำดับ')
h3('2.7.4  Terrain Effects และน้ำ')
body('TerrainEffects เก็บพื้นที่น้ำเป็น CollisionShape ที่ไม่แข็ง เมื่อเท้าอยู่ในพื้นที่น้ำ ความเร็วจะถูกคูณด้วย WATER_SPEED_MULTIPLIER (0.80) และใช้ travelCost() ในการประเมินเส้นทางของบอท โดยคิดค่าน้ำเป็นระยะทางที่แพงขึ้นตามความ aversion ของบุคลิกภาพ')
h3('2.7.5  AI ของบอท')
body('บอทใช้ BotRouteGraph ซึ่งเป็นกราฟมีทิศทาง จุดแต่ละจุดมี order, next และ nextDifficulty สถานะในการค้นหาประกอบด้วย (waypoint, จำนวนจุดตรวจที่ผ่าน, finished) และใช้ Dijkstra เพื่อหาเส้นทางที่มีต้นทุนต่ำสุด โดยต้นทุนประกอบด้วยระยะทาง ค่าน้ำ ค่าความยากของเส้นเชื่อม และอิทธิพลจากรถคันอื่น')
body('ที่ทางแยก บอทจะเลือกเส้นทางจากกราฟ เช่น เส้นทางหลักหรือเส้นทางอ้อมที่ลึกกว่า บอทสองตัวถูกกำหนดให้เลือกเส้นทางอ้อมเพื่อให้การแข่งขันมีความหลากหลาย ส่วนบอทตัวอื่นเลือกเส้นทางหลัก')
body('BotController แปลงจุดหมายเป็นคำสั่งแปดทิศ (ซ้าย/ขวา/ขึ้น/ลง/ทแยง) และใช้ BotNavigation ซึ่งเป็น A* บนกริดขนาด 16 พิกเซลเพื่อหาเส้นทางอ้อมระยะสั้นเมื่อมีสิ่งกีดขวางหรือน้ำ หากหาไม่พบจะใช้ fallbackMove() เพื่อลองก้าวข้ามหรือหลบด้านข้างแทนการหยุดนิ่ง')
h3('2.7.6  แอนิเมชันและการเรียงลำดับภาพ')
body('TiledMap อ่านแอนิเมชันของ tile จาก TSX และใช้เวลา animationSeconds ในการเลือกเฟรมตาม duration ของแต่ละ frame ส่วน SpriteAnimation ใช้กับภาพผู้แข่งขันและแอนิเมชันวิ่ง โดยเปลี่ยนเฟรมตามเวลาและสถานะ moving/sprinting')
h3('2.7.7  การต่อสู้และการชาร์จ')
body('CombatSystem ตรวจระยะการโจมตี มุมหรือทิศทาง และสถานะการป้องกันของผู้ถูกโจมตี หากโจมตีถูกจะเกิดสตั๊น/สโลว์หรือโล่ป้องกัน ขึ้นอยู่กับสถานะ CombatState และ RacerStatus ส่วน ChargeState ตรวจเวลากด O ค้าง หากชาร์จครบ 3–5 วินาทีจะได้ stamina ตามเวลา และหากชนสิ่งกีดขวางหรือผ่านน้ำระหว่างชาร์จจะถือว่าล้มเหลวและติดสตั๊น เว้นแต่มีโล่ป้องกัน')

# Chapter 3
doc.add_page_break()
h1('บทที่ 3  สรุป')
h2('3.1  ปัญหาที่พบระหว่างการพัฒนา')
body('ระหว่างการพัฒนาเกมพบปัญหาหลายลักษณะ ตั้งแต่ข้อมูลแผนที่ ระบบชน ระบบบอท ไปจนถึงส่วนติดต่อผู้ใช้ ตารางที่ 5 สรุปปัญหาสำคัญและแนวทางแก้ไข')
table(['ปัญหา', 'สาเหตุ', 'แนวทางแก้ไข'],
      [['โหลดแผนที่แล้วได้ค่า null', 'ค่า treeBaseX/treeBaseY ของ tileset ต้นไม้อยู่นอกขนาดภาพ ทำให้ TiledMap โหลดไม่สำเร็จ', 'ปรับค่าจุดฐานให้อยู่ในขอบเขตภาพและตรวจสอบขนาด tileset ก่อนโหลด'],
       ['ผู้เล่นไม่เคลื่อนที่ลงเมื่อกด O+S+Space', 'การเริ่มการแข่งขันล้าง InputState ที่ผู้เล่นกดค้างไว้', 'เก็บค่า movement/charge ที่กดค้างไว้เมื่อเริ่มการแข่งขันและเมื่อถึง GO และล้างสถานะ Space เมื่อปล่อย'],
       ['บอทหยุดนิ่งหรือวนโจมตี/ป้องกัน', 'การวางแผนเส้นทางที่จุดแยกคืนค่าว่าง ทำให้ controller ไม่มีเป้าหมายและหยุด', 'แก้จุดเริ่มต้นของ planner ตรวจสอบ plan ซ้ำ และมี fallback movement'],
       ['บอทเลือกเส้นทางอ้อมไม่ได้', 'กราฟ waypoint ไม่มีเส้นเชื่อมไปยังจุดเริ่มเส้นทางอ้อม', 'เพิ่ม next ของ waypoint ทางแยกและกำหนดบอทสองตัวให้เลือกเส้นทางอ้อม'],
       ['กรอบไอเทมเปลี่ยนตามแอนิเมชัน', 'getArea() คำนวณจากพิกเซลที่มองเห็นของเฟรมปัจจุบัน', 'กำหนด hitbox คงที่จากขนาดภาพไอเทมและใช้ hitbox เดียวตลอดแอนิเมชัน'],
       ['ไฟล์ภาพมีขนาดใหญ่และอยู่ภายนอก JAR', 'asset-packs มีไฟล์ต้นฉบับจำนวนมาก และโค้ดโหลดจาก File', 'จัดส่ง JAR พร้อม assets/asset-packs หรือบีบอัดเป็นไฟล์ ZIP สำหรับส่งอาจารย์']],
      widths=[Cm(5.0), Cm(6.0), Cm(4.8)], font_size=11.2)
body('ปัญหาที่สำคัญที่สุดคือการทำงานร่วมกันระหว่างข้อมูลจาก Tiled กับโค้ด Java เนื่องจาก Tiled เก็บพิกัดและขนาดภาพในหน่วยพิกเซล แต่โค้ดต้องแปลงเป็นตำแหน่ง tile และ world coordinate หลายชั้น ทำให้ข้อผิดพลาดเล็กน้อย เช่น ค่าจุดฐานต้นไม้เกินภาพ ส่งผลให้โหลดแผนที่ทั้งแผนที่ไม่ได้')
body('ข้อจำกัดที่ยังเหลืออยู่ ได้แก่ เกมมีแผนที่เดียว ไม่มีระบบเสียง ไม่มีระบบบันทึกเกม และไฟล์ภาพไม่ได้ถูกฝังใน JAR ทำให้การส่งต่อต้องระวังเรื่อง path ของ assets และ asset-packs')
h2('3.2  จุดเด่นของโปรแกรม')
body('จุดเด่นของ Master of Reins คือการเป็นเกมที่พัฒนาด้วย Java พื้นฐานโดยไม่ใช้ game engine แต่มีองค์ประกอบครบทั้งการแข่งขัน การต่อสู้ ไอเทม บอท และส่วนติดต่อผู้ใช้ที่ใช้งานได้จริง')
bullet('ระบบแผนที่จาก Tiled ที่รองรับหลายเลเยอร์ หลาย tileset ภาพต้นไม้ และ object layer สำหรับ collision/race/wet')
bullet('ระบบชนแบบ swept collision ที่รองรับรูปหลายเหลี่ยม วงรี และการเลื่อนไปตามขอบ ทำให้การเคลื่อนที่ลื่นและไม่ทะลุกำแพงบาง')
bullet('ระบบเรียงลำดับต้นไม้ตามความลึก (Y-sorting) และการลดความทึบของต้นไม้เมื่อบังผู้เล่น')
bullet('บอทที่ใช้กราฟ waypoint, Dijkstra, A* ระยะสั้น และบุคลิกภาพที่แตกต่างกัน ทำให้การแข่งขันมีรูปแบบหลากหลาย')
bullet('ระบบ stamina, sprint, ไอเทม, โล่, การโจมตี, การป้องกัน และการชาร์จพลังที่ทำงานร่วมกัน')
bullet('โค้ดแบ่งแพ็กเกจตามความรับผิดชอบและใช้ OOP อย่างสม่ำเสมอ ทำให้อ่านและต่อยอดได้ง่าย')
body('โดยรวม โครงการนี้แสดงให้เห็นว่าการนำองค์ความรู้ด้านการเขียนโปรแกรมเชิงวัตถุ อัลกอริทึม และการออกแบบส่วนติดต่อผู้ใช้มาประกอบกันเป็นเกมที่เล่นได้จริงเป็นไปได้ แม้จะอยู่ในขอบเขตของรายวิชาและใช้เวลาพัฒนาจำกัด')

# References
h1('เอกสารอ้างอิง')
for ref in [
    'Oracle. (2025). Java Platform, Standard Edition Documentation. https://docs.oracle.com/en/java/',
    'Tiled. (2025). Tiled Documentation: TMX Map Format and TSX Tileset Format. https://doc.mapeditor.org/',
    'Hart, P. E., Nilsson, N. J., & Raphael, B. (1968). A Formal Basis for the Heuristic Determination of Minimum Cost Paths. IEEE Transactions on Systems Science and Cybernetics.',
    'Red Blob Games. (2024). Introduction to A* and Dijkstra pathfinding. https://www.redblobgames.com/pathfinding/',
    'GandalfHardcore. (2024). GandalfHardcore FREE Platformer Assets. (สื่อประกอบเกม)',
    'REalOne. (2024). Pixel Crawler - Free Pack. (สื่อประกอบเกม)'
]:
    p = doc.add_paragraph()
    p.paragraph_format.line_spacing = 1.3
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.left_indent = Cm(0.8)
    p.paragraph_format.first_line_indent = Cm(-0.8)
    r = p.add_run(ref)
    set_font(r, 14)

# Appendix
h1('ภาคผนวก ก  แนวทางการติดตั้งและรันโปรแกรม')
body('ภาคผนวกนี้ใช้เป็นคู่มือสำหรับอาจารย์หรือผู้ตรวจโครงการในการเปิดเกม และเป็นที่สำหรับแนบ Proposal ฉบับเต็มตามข้อกำหนดของรายวิชา')
h2('ก.1  สิ่งที่ต้องเตรียม')
bullet('Java Runtime Environment (JRE) หรือ JDK เวอร์ชัน 17 ขึ้นไป')
bullet('ไฟล์ MasterOfReins.jar')
bullet('โฟลเดอร์ assets และ asset-packs ที่อยู่ระดับเดียวกับไฟล์ JAR')
h2('ก.2  วิธีการรัน')
body('เปิด Command Prompt หรือ PowerShell ที่โฟลเดอร์ซึ่งมีไฟล์ JAR แล้วพิมพ์')
code(['java -jar MasterOfReins.jar'], 'คำสั่งรันเกม')
body('หากเกมแสดงข้อความว่าโหลดแผนที่ไม่ได้ ให้ตรวจสอบว่าโฟลเดอร์ assets/Map มี Background.tmx และไฟล์ TSX ครบ รวมถึงโฟลเดอร์ asset-packs อยู่ถูกตำแหน่ง')
h2('ก.3  Proposal')
body('[ ส่วนนี้สำหรับแนบ Proposal ฉบับจริงของรายวิชา ]')

cp = doc.core_properties
cp.title = 'รายงานโครงงาน Master of Reins'
cp.subject = 'รายงานโครงงานเกมแข่งม้า 2 มิติ'
cp.author = '[ชื่อ-นามสกุล]'
cp.language = 'th-TH'
cp.keywords = 'Java, Swing, Game, OOP, Tiled, AI, Master of Reins'
out = 'MasterOfReins_Report.docx'
doc.save(out)
print('saved', out)

