from pathlib import Path
from collections import Counter
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
def issues(file):
    items=ET.parse(file).getroot().findall('issue')
    counter=Counter()
    for i in items:
        path=i.find('location').get('file','').replace('\\','/').split('/app/',1)[-1]
        counter[(i.get('severity'),i.get('id'),path,i.get('message'),i.get('errorLine1','').strip())]+=1
    return counter
base=issues('D:/Claude/experiments/lunaxy-v923-lint/app/build/reports/lint-results-debug.xml')
new=issues(root/'app/build/reports/lint-results-debug.xml')
for label,delta in [('ADDED',new-base),('REMOVED',base-new)]:
    print(label)
    for item,count in delta.items(): print(count,*item,sep=' | ')
print('New errors:',sum(count for item,count in (new-base).items() if item[0]=='Error'))
