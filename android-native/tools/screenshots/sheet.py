import sys,glob
from PIL import Image
fs=sorted(glob.glob(f'raw/{sys.argv[1]}-*.png')); print(fs)
W,H=640,360; cols=3; rows=(len(fs)+2)//3
sheet=Image.new('RGB',(W*cols,H*rows))
for i,f in enumerate(fs): sheet.paste(Image.open(f).convert('RGB').resize((W,H)),((i%cols)*W,(i//cols)*H))
sheet.save(f'sheet-{sys.argv[1]}.png')
