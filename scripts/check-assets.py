from pathlib import Path
import json,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'app/src/main/res'
A='{http://schemas.android.com/apk/res/android}'
count=0
for svg in sorted((ROOT/'third_party/tabler').glob('*.svg')):
    expected=[p.attrib['d'] for p in ET.parse(svg).getroot()]
    vector=ET.parse(RES/'drawable'/('ic_tabler_'+svg.stem.replace('-','_')+'.xml')).getroot()
    assert [p.attrib[A+'pathData'] for p in vector]==expected,svg.name
    for path in vector:
        assert path.attrib[A+'strokeWidth']=='2' and path.attrib[A+'strokeLineCap']=='round' and path.attrib[A+'strokeLineJoin']=='round'
    count+=1
colors=json.loads((ROOT/'third_party/radix-colors.json').read_text(encoding='utf-8'))
for mode,folder in [('light','values'),('dark','values-night')]:
    for item in ET.parse(RES/folder/'colors.xml').getroot():
        if item.attrib['name'].startswith('radix_'):
            assert item.text==colors[mode][item.attrib['name'][6:]]
            count+=1
# The launcher logo is fixed: the floating dot's ring on the Slate 12 tile.
icons={item.attrib['name']:item.text for item in ET.parse(RES/'values/colors.xml').getroot()}
assert icons['icon_background']==colors['light']['slate12'],'icon background'
assert icons['icon_foreground']==colors['light']['slate1'],'icon foreground'
logo=[p.attrib[A+'pathData'] for p in ET.parse(RES/'drawable/ic_launcher_foreground.xml').getroot()]
assert len(logo)==1 and 'a13,13' in logo[0],'launcher logo is the dot ring'
adaptive={child.tag.split('}')[-1] for child in ET.parse(RES/'mipmap-anydpi/ic_launcher.xml').getroot()}
assert adaptive=={'background','foreground','monochrome'},'adaptive icon layers'
count+=4
def luminance(color):
    values=[int(color[i:i+2],16)/255 for i in (1,3,5)]
    values=[v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4 for v in values]
    return sum(v*w for v,w in zip(values,[.2126,.7152,.0722]))
for mode,palette in colors.items():
    for fg,bg in [('slate12','slate1'),('slate11','slate1'),('blue12' if mode=='light' else 'blue11','blue3'),('blue11','slate1')]:
        lo,hi=sorted([luminance(palette[fg]),luminance(palette[bg])])
        assert (hi+.05)/(lo+.05)>=4.5,(mode,fg,bg)
        count+=1
print(f'PASS: {count} source-geometry, palette-value and text-contrast checks')
