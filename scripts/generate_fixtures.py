"""Run with bundled Python (reportlab + pypdf). System Cairo/Pango + Poppler required."""
from pathlib import Path
import ctypes as C
import subprocess
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase.pdfmetrics import EmbeddedType1Face,Font
from pypdf import PdfReader,PdfWriter
from pypdf.generic import DictionaryObject as D,NameObject as N,NumberObject as I,ArrayObject as A,DecodedStreamObject as S
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/androidTest/assets/pdf';OUT.mkdir(parents=True,exist_ok=True)
REF=ROOT/'app/src/androidTest/assets/reference';REF.mkdir(exist_ok=True)
fonts=Path('/usr/share/fonts/truetype/dejavu')
for name,file in [('Regular','DejaVuSans.ttf'),('Bold','DejaVuSans-Bold.ttf'),('Italic','DejaVuSansMono-Oblique.ttf')]:pdfmetrics.registerFont(TTFont(name,str(fonts/file)))
face=EmbeddedType1Face('/usr/share/fonts/type1/urw-base35/NimbusRoman-Regular.afm','/usr/share/fonts/X11/Type1/NimbusRoman-Regular.pfb')
pdfmetrics.registerTypeFace(face);pdfmetrics.registerFont(Font('TypeOne',face.name,'WinAnsiEncoding'))
c=canvas.Canvas(str(OUT/'embedded.pdf'),pagesize=(600,800),invariant=1)
for font,y in [('Regular',680),('Bold',600),('Italic',520)]:
 c.setFont(font,18);c.drawString(45,y,'Шрифты PDF: Кириллица / Latin 0123456789');c.drawString(45,y-35,'ﬁ ﬂ  ∑ ∫ √ ∞ ≤ ≥ ± Ω α β')
c.setFont('TypeOne',22);c.drawString(45,400,'Embedded Type 1: Nimbus Roman 012345')
c.setFont('Regular',14);c.drawString(45,100,'Subset TrueType + full Type 1. White page, black text.');c.save()
# A genuinely full TrueType embedding, with a simple WinAnsi font for Latin test text.
w=PdfWriter();p=w.add_blank_page(width=600,height=800)
stream=S();stream.set_data((fonts/'DejaVuSans.ttf').read_bytes());stream[N('/Length1')]=I(len(stream.get_data()));ref=w._add_object(stream)
desc=D({N('/Type'):N('/FontDescriptor'),N('/FontName'):N('/DejaVuSans'),N('/Flags'):I(32),N('/FontBBox'):A([I(-1021),I(-463),I(1793),I(1232)]),N('/ItalicAngle'):I(0),N('/Ascent'):I(928),N('/Descent'):I(-236),N('/CapHeight'):I(729),N('/StemV'):I(80),N('/FontFile2'):ref})
rl=pdfmetrics.getFont('Regular')
f=D({N('/Type'):N('/Font'),N('/Subtype'):N('/TrueType'),N('/BaseFont'):N('/DejaVuSans'),N('/FirstChar'):I(32),N('/LastChar'):I(126),N('/Widths'):A([I(round(pdfmetrics.stringWidth(chr(i),'Regular',1000))) for i in range(32,127)]),N('/FontDescriptor'):w._add_object(desc),N('/Encoding'):N('/WinAnsiEncoding')})
p[N('/Resources')]=D({N('/Font'):D({N('/F1'):w._add_object(f)})})
s=S();s.set_data(b'BT /F1 24 Tf 45 680 Td (Full embedded TrueType: ABC xyz 012345) Tj ET');p[N('/Contents')]=w._add_object(s)
w.write(str(OUT/'full-truetype.pdf'))
# Pango shapes RTL and CJK, Cairo embeds the selected OpenType/CFF subsets.
cairo=C.CDLL('libcairo.so.2');pc=C.CDLL('libpangocairo-1.0.so.0');pa=C.CDLL('libpango-1.0.so.0');go=C.CDLL('libgobject-2.0.so.0')
def bind(lib,name,rest,args):
 f=getattr(lib,name);f.restype=rest;f.argtypes=args;return f
ptr=C.c_void_p;dbl=C.c_double;integer=C.c_int;string=C.c_char_p
surf=bind(cairo,'cairo_pdf_surface_create',ptr,[string,dbl,dbl])(str(OUT/'scripts-cff.pdf').encode(),600,800)
cr=bind(cairo,'cairo_create',ptr,[ptr])(surf)
layout=bind(pc,'pango_cairo_create_layout',ptr,[ptr])(cr)
for family,text,y in [('Noto Sans CJK SC','OpenType CFF: Regular 012345 fi fl',100),('Noto Sans CJK SC Bold','OpenType CFF Bold',170),('Noto Sans CJK SC Italic','OpenType CFF Italic',240),('Noto Naskh Arabic','مرحبا بالعالم ١٢٣',330),('Noto Sans CJK SC','中文 日本語 한국어 123',430)]:
 desc=bind(pa,'pango_font_description_from_string',ptr,[string])((family+' 22').encode())
 bind(pa,'pango_layout_set_font_description',None,[ptr,ptr])(layout,desc)
 bind(pa,'pango_layout_set_text',None,[ptr,string,integer])(layout,text.encode(),-1)
 bind(cairo,'cairo_move_to',None,[ptr,dbl,dbl])(cr,45,y)
 bind(pc,'pango_cairo_show_layout',None,[ptr,ptr])(cr,layout)
 bind(pa,'pango_font_description_free',None,[ptr])(desc)
bind(go,'g_object_unref',None,[ptr])(layout);bind(cairo,'cairo_destroy',None,[ptr])(cr);bind(cairo,'cairo_surface_destroy',None,[ptr])(surf)
c=canvas.Canvas(str(OUT/'fallback.pdf'),pagesize=(600,800),invariant=1);c.setFont('Helvetica',24);c.drawString(45,650,'Non-embedded Helvetica 012345 ABC xyz');c.save()
# Same Cyrillic encoding/widths as embedded specimen, but deliberately omit the font program.
r=PdfReader(str(OUT/'embedded.pdf'));w=PdfWriter();w.add_page(r.pages[0])
for f in w.pages[0]['/Resources']['/Font'].values():
 obj=f.get_object();d=obj.get('/FontDescriptor')
 if d:
  d=d.get_object()
  for key in ['/FontFile','/FontFile2','/FontFile3']:
   if key in d:del d[key]
w.write(str(OUT/'fallback-cyrillic.pdf'))
c=canvas.Canvas(str(OUT/'mixed.pdf'),invariant=1)
for size in [(600,800),(800,500),(400,1000)]:
 c.setPageSize(size);c.setFont('Regular',24);c.drawString(30,size[1]-60,f'Страница {size[0]} × {size[1]}');c.showPage()
c.save()
c=canvas.Canvas(str(OUT/'large.pdf'),pagesize=(600,800),invariant=1)
for i in range(300):
 c.setFont('Regular',24);c.drawString(45,650,f'Страница {i+1} / 300');c.showPage()
c.save()
for name in ['embedded','full-truetype','scripts-cff','fallback','fallback-cyrillic','mixed','large']:
 subprocess.run(['pdftoppm','-f','1','-singlefile','-scale-to','800','-png',str(OUT/(name+'.pdf')),str(REF/name)],check=True)
c=canvas.Canvas(str(OUT/'scan.pdf'),pagesize=(600,800),invariant=1);c.drawImage(str(REF/'embedded.png'),0,0,600,800);c.save()
subprocess.run(['pdftoppm','-f','1','-singlefile','-scale-to','800','-png',str(OUT/'scan.pdf'),str(REF/'scan')],check=True)
print('Generated 8 PDF fixtures and reference images')
