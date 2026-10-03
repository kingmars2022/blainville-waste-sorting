# -*- coding: utf-8 -*-
"""Emits V17 from the items printed on the city's own 2026 calendar, page 2."""
import io

SRC = {
 'organic':   'https://blainville.ca/services/environnement-et-voirie/matieres-organiques',
 'recycling': 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables',
 'garbage':   'https://blainville.ca/services/environnement-et-voirie/ordures-menageres',
 'bulky':     'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles',
}
BIN = {'organic': 'brown', 'recycling': 'blue', 'garbage': 'black', 'bulky': 'none'}

# (dest, name fr/en/zh, instruction fr/en/zh, examples fr/en/zh, keywords fr/en/zh)
#
# "Vaisselle jetable en carton" is on the city's list and deliberately absent
# here: item 6 already answers it ("Pizza boxes, paper plates and paper towels
# go in the brown bin"). Adding it cost the guide a correct answer - "Where
# does soiled cardboard go?" started returning the paper-plate entry instead
# of the food-soiled one, because the shorter document scored higher on the
# shared word. A near-duplicate entry does not add coverage, it splits it.
ITEMS = [
 ('organic',
  ("Viandes et os", "Meat and bones", "肉类和骨头"),
  ("Les viandes crues ou cuites vont au bac brun, avec les os et le gras.",
   "Raw or cooked meat goes in the brown bin, bones and fat included.",
   "生熟肉类连同骨头和油脂一起放入棕桶。"),
  (["viande crue","viande cuite","os de poulet","gras"], ["raw meat","cooked meat","chicken bones","fat"], ["生肉","熟肉","骨头","油脂"]),
  # "os" is two letters, below MySQL's innodb_ft_min_token_size, so it is never
  # indexed and never matches. The eval caught it: "Ou jeter les os de poulet ?"
  # was refused while the English "Where do chicken bones go?" was answered,
  # because "bones" is long enough to be a token. The fix is words a resident
  # actually types that the index can hold.
  (["viande","poulet","volaille","gras","ossements"], ["meat","chicken","poultry","bones","fat"], ["肉","鸡肉","骨头","油脂"])),

 ('organic',
  ("Poissons et fruits de mer", "Fish and seafood", "鱼类和海鲜"),
  ("Les poissons, leurs aretes et les fruits de mer vont au bac brun.",
   "Fish, fish bones and seafood go in the brown bin.",
   "鱼、鱼骨和海鲜放入棕桶。"),
  (["poisson","aretes","crevettes","coquillages"], ["fish","fish bones","shrimp","shellfish"], ["鱼","鱼骨","虾","贝类"]),
  (["poisson","aretes","fruits de mer"], ["fish","seafood","bones"], ["鱼","鱼骨","海鲜"])),

 ('organic',
  ("Oeufs et coquilles", "Eggs and eggshells", "鸡蛋和蛋壳"),
  ("Les oeufs et leurs coquilles vont au bac brun.",
   "Eggs and their shells go in the brown bin.",
   "鸡蛋和蛋壳放入棕桶。"),
  (["oeufs","coquilles d'oeufs"], ["eggs","eggshells"], ["鸡蛋","蛋壳"]),
  (["oeuf","coquille"], ["egg","eggshell"], ["鸡蛋","蛋壳"])),

 ('organic',
  ("Produits laitiers solides", "Solid dairy products", "固体乳制品"),
  ("Le fromage, le beurre et le yogourt vont au bac brun.",
   "Cheese, butter and yogurt go in the brown bin.",
   "奶酪、黄油和酸奶放入棕桶。"),
  (["fromage","beurre","yogourt"], ["cheese","butter","yogurt"], ["奶酪","黄油","酸奶"]),
  (["fromage","beurre","yogourt","laitier"], ["cheese","butter","yogurt","dairy"], ["奶酪","黄油","酸奶"])),

 ('organic',
  ("Residus de the et de cafe", "Tea and coffee grounds", "茶渣和咖啡渣"),
  ("Les residus de the, de tisane et de cafe vont au bac brun.",
   "Tea, herbal tea and coffee residue go in the brown bin.",
   "茶叶、花草茶和咖啡渣放入棕桶。"),
  (["marc de cafe","sachets de the","tisane"], ["coffee grounds","tea bags","herbal tea"], ["咖啡渣","茶包","花草茶"]),
  (["cafe","the","tisane","marc"], ["coffee","tea","grounds"], ["咖啡","茶","茶渣"])),

 ('organic',
  ("Mouchoirs et serviettes de table", "Tissues and paper napkins", "纸巾和餐巾纸"),
  ("Les mouchoirs, serviettes de table et nappes en papier vont au bac brun.",
   "Tissues, paper napkins and paper tablecloths go in the brown bin.",
   "纸巾、餐巾纸和纸桌布放入棕桶。"),
  (["mouchoirs","serviettes de table","nappes en papier"], ["tissues","paper napkins","paper tablecloths"], ["纸巾","餐巾纸","纸桌布"]),
  (["mouchoir","serviette","nappe"], ["tissue","napkin","tablecloth"], ["纸巾","餐巾","桌布"])),

 ('organic',
  ("Cure-dents et batonnets en bois", "Toothpicks and wooden sticks", "牙签和木棒"),
  ("Les cure-dents et les batonnets en bois vont au bac brun.",
   "Toothpicks and wooden sticks go in the brown bin.",
   "牙签和木制小棒放入棕桶。"),
  (["cure-dents","batonnets en bois","brochettes"], ["toothpicks","wooden sticks","skewers"], ["牙签","木棒","竹签"]),
  (["cure-dent","batonnet","bois"], ["toothpick","wooden stick"], ["牙签","木棒"])),

 ('recycling',
  ("Bouteille de vin", "Wine bottle", "酒瓶"),
  ("Les bouteilles de vin vont au bac bleu, avec leur bouchon ou leur couvercle.",
   "Wine bottles go in the blue bin, with their cap or lid.",
   "酒瓶连同瓶盖放入蓝桶。"),
  (["bouteille de vin","bouteille en verre"], ["wine bottle","glass bottle"], ["酒瓶","玻璃瓶"]),
  (["vin","bouteille","verre"], ["wine","bottle","glass"], ["酒瓶","玻璃瓶"])),

 ('recycling',
  ("Bouteille d'huile", "Oil bottle", "食用油瓶"),
  ("Les bouteilles d'huile, en verre ou en plastique, vont au bac bleu.",
   "Oil bottles, glass or plastic, go in the blue bin.",
   "食用油瓶，玻璃或塑料，均放入蓝桶。"),
  (["bouteille d'huile","huile d'olive"], ["oil bottle","olive oil"], ["油瓶","橄榄油瓶"]),
  (["huile","bouteille"], ["oil","bottle"], ["油瓶"])),

 ('recycling',
  ("Boite de conserve", "Food can", "罐头盒"),
  ("Les conserves vont au bac bleu. Les vider et les rincer legerement.",
   "Food cans go in the blue bin. Empty and lightly rinse them.",
   "罐头盒放入蓝桶，倒空并简单冲洗。"),
  (["conserve","boite de thon","boite de soupe"], ["can","tuna can","soup can"], ["罐头","金枪鱼罐头","汤罐"]),
  (["conserve","boite","metal"], ["can","tin","metal"], ["罐头","铁罐"])),

 ('recycling',
  ("Boite a oeufs et casseau de fruits", "Egg carton and fruit basket", "蛋盒和水果盒"),
  ("Les boites a oeufs et les casseaux de fruits vont au bac bleu : ce sont des emballages.",
   "Egg cartons and fruit baskets go in the blue bin: they are packaging.",
   "蛋盒和水果盒属于包装，放入蓝桶。"),
  (["boite a oeufs","casseau de fraises"], ["egg carton","strawberry basket"], ["蛋盒","草莓盒"]),
  (["boite a oeufs","casseau","emballage"], ["egg carton","fruit basket","packaging"], ["蛋盒","水果盒"])),

 ('recycling',
  ("Sac de croustilles", "Chip bag", "薯片袋"),
  ("Les sacs de croustilles vont au bac bleu : ils servent a transporter un produit, donc ce sont des emballages.",
   "Chip bags go in the blue bin: they carry a product, so they count as packaging.",
   "薯片袋用于盛装产品，属于包装，放入蓝桶。"),
  (["sac de croustilles","sac de chips"], ["chip bag","snack bag"], ["薯片袋","零食袋"]),
  (["croustilles","chips","sac"], ["chips","snack bag"], ["薯片袋","零食袋"])),

 ('recycling',
  ("Sac de papier", "Paper bag", "纸袋"),
  ("Les sacs de papier vont au bac bleu.",
   "Paper bags go in the blue bin.",
   "纸袋放入蓝桶。"),
  (["sac de papier","sac brun"], ["paper bag","brown bag"], ["纸袋","牛皮纸袋"]),
  (["sac papier"], ["paper bag"], ["纸袋"])),

 ('garbage',
  ("Vaisselle et sacs compostables ou biodegradables", "Compostable or biodegradable dishes and bags", "可堆肥或可降解餐具和塑料袋"),
  ("Au bac noir, malgre leur nom. Ils se decomposent en petits fragments qui contaminent le plastique recyclable, et ils ne se degradent pas assez vite pour le bac brun.",
   "In the black bin, despite the name. They break into small fragments that contaminate recyclable plastic, and they do not break down fast enough for the brown bin.",
   "尽管名字如此，仍放入黑桶。它们会碎成小颗粒污染可回收塑料，而分解速度又达不到棕桶的要求。"),
  (["ustensiles compostables","sacs biodegradables","contenants compostables"],
   ["compostable utensils","biodegradable bags","compostable containers"],
   ["可堆肥餐具","可降解塑料袋","可堆肥容器"]),
  (["compostable","biodegradable","ustensile"], ["compostable","biodegradable","utensil"], ["可堆肥","可降解","餐具"])),

 ('garbage',
  ("Essuie-tout souille de produits chimiques", "Paper towel with chemicals on it", "沾过化学品的厨房纸"),
  ("Au bac noir. Le meme essuie-tout va au bac brun s'il n'a servi qu'a de la nourriture.",
   "In the black bin. The same paper towel goes in the brown bin if it only touched food.",
   "放入黑桶。同样的厨房纸，如果只沾过食物，则应放入棕桶。"),
  (["essuie-tout avec nettoyant","chiffon avec produit"], ["paper towel with cleaner","wipe with chemicals"], ["沾清洁剂的厨房纸","沾化学品的抹布"]),
  (["essuie-tout","produit chimique","nettoyant"], ["paper towel","chemical","cleaner"], ["厨房纸","化学品","清洁剂"])),

 ('garbage',
  ("Ampoule electrique", "Light bulb", "灯泡"),
  ("Les ampoules autres que fluocompactes vont au bac noir. Les fluocompactes se rapportent a l'ecocentre.",
   "Bulbs other than compact fluorescent go in the black bin. Compact fluorescent bulbs go to the ecocentre.",
   "除节能灯（紧凑型荧光灯）外的灯泡放入黑桶；节能灯需送到生态中心。"),
  (["ampoule incandescente","ampoule DEL"], ["incandescent bulb","LED bulb"], ["白炽灯泡","LED 灯泡"]),
  (["ampoule","lumiere"], ["bulb","light"], ["灯泡"])),

 ('garbage',
  ("Vaisselle cassee", "Broken dishes", "破碎餐具"),
  ("La vaisselle cassee va au bac noir. Les morceaux de vitre ou de miroir casses vont plutot a la collecte d'encombrants.",
   "Broken dishes go in the black bin. Broken glass or mirror pieces go to the bulky collection instead.",
   "破碎餐具放入黑桶；碎玻璃和碎镜子则属于大件垃圾收集。"),
  (["assiette cassee","tasse cassee"], ["broken plate","broken cup"], ["破盘子","破杯子"]),
  (["vaisselle cassee","assiette"], ["broken dish","plate"], ["破碎餐具","盘子"])),

 ('garbage',
  ("Plastique sans symbole", "Plastic with no recycling symbol", "无回收标志的塑料"),
  ("Au bac noir si ce n'est ni un contenant ni un emballage. Le bac bleu accepte les contenants, les emballages et les imprimes, c'est tout.",
   "In the black bin if it is neither a container nor packaging. The blue bin takes containers, packaging and printed paper, and nothing else.",
   "若既不是容器也不是包装，放入黑桶。蓝桶只收容器、包装和印刷品，仅此三类。"),
  (["objet en plastique","piece en plastique"], ["plastic object","plastic part"], ["塑料物件","塑料零件"]),
  (["plastique","symbole"], ["plastic","symbol"], ["塑料","标志"])),

 ('bulky',
  ("Morceaux de vitre ou de miroir casses", "Broken glass or mirror", "碎玻璃和碎镜子"),
  ("A la collecte d'encombrants, pas au bac noir. Les deposer de facon securitaire, dans une boite en carton ou un sac en papier.",
   "To the bulky collection, not the black bin. Put them out safely, in a cardboard box or a paper bag.",
   "属于大件垃圾收集，不要放入黑桶。请安全地装入纸箱或纸袋后放置。"),
  (["vitre cassee","miroir casse"], ["broken window glass","broken mirror"], ["碎玻璃","碎镜子"]),
  (["vitre","miroir","verre casse"], ["glass","mirror","broken glass"], ["玻璃","镜子","碎玻璃"])),

 ('bulky',
  ("Chauffe-eau, lavabo et toilette", "Water heater, sink and toilet", "热水器、洗手盆和马桶"),
  ("A la collecte d'encombrants, sur demande en ligne. Maximum 90 kg et 1,8 m sur 1,2 m par article.",
   "To the bulky collection, on request. Maximum 90 kg and 1.8 m by 1.2 m per item.",
   "属于大件垃圾收集，需在线申请。单件最重 90 公斤，最大 1.8 米 × 1.2 米。"),
  (["chauffe-eau","lavabo","toilette"], ["water heater","sink","toilet"], ["热水器","洗手盆","马桶"]),
  (["chauffe-eau","lavabo","toilette"], ["water heater","sink","toilet"], ["热水器","洗手盆","马桶"])),

 ('bulky',
  ("Electromenager et BBQ", "Appliance and BBQ", "家电和烧烤炉"),
  ("A la collecte d'encombrants : electromenagers sans halocarbure, BBQ sans bonbonne. Un appareil contenant un halocarbure se rapporte a l'ecocentre.",
   "To the bulky collection: appliances without halocarbon, BBQ without its tank. An appliance containing halocarbon goes to the ecocentre.",
   "属于大件垃圾收集：不含卤代烃的家电、不带气罐的烧烤炉。含卤代烃的电器须送生态中心。"),
  (["cuisiniere","laveuse","BBQ sans bonbonne"], ["stove","washing machine","BBQ without tank"], ["炉灶","洗衣机","不带气罐的烧烤炉"]),
  (["electromenager","BBQ","halocarbure"], ["appliance","BBQ","halocarbon"], ["家电","烧烤炉"])),
]

def q(s):
    return "'" + s.replace("'", "''") + "'"

out = []
out.append("""-- The sorting guide, from the city's own list rather than a sample of it.
--
-- Source: Ville de Blainville, "Calendrier des collectes residentielles 2026",
-- page 2 - the bac brun / bac bleu / bac noir / encombrants panels. V3 seeded
-- broad categories ("Contenants et emballages"); this adds the specific
-- objects the document actually names, which are the words a resident types.
--
-- Chosen for where the answer is surprising, because that is where a sorting
-- guide earns its keep. Compostable utensils go in the BLACK bin, not the
-- brown one, and the document says why; the same paper towel is organic or
-- garbage depending on whether it met food or a cleaning product; broken
-- mirror is a bulky pickup rather than a bin.
--
-- Generated by a script rather than typed, like V11: 22 items across three
-- languages over four tables is exactly the transcription where a silent
-- mistake hides. The ids come from last_insert_id() rather than being
-- assumed, so this does not depend on where auto_increment happens to be.
""")

for dest, names, instr, examples, keywords in ITEMS:
    out.append("insert into sorting_item (destination_type, bin_color, source_url) values "
               f"({q(dest)}, {q(BIN[dest])}, {q(SRC[dest])});")
    out.append("set @id = last_insert_id();")
    rows = ", ".join(f"(@id, {q(lang)}, {q(n)}, {q(i)})"
                     for lang, n, i in zip(('fr', 'en', 'zh'), names, instr))
    out.append(f"insert into sorting_item_translation (item_id, language_code, name, instruction) values {rows};")
    ex = []
    for lang, values in zip(('fr', 'en', 'zh'), examples):
        ex += [f"(@id, {q(lang)}, {pos}, {q(v)})" for pos, v in enumerate(values)]
    out.append("insert into sorting_item_example (item_id, language_code, position, example) values "
               + ", ".join(ex) + ";")
    kw = []
    for lang, values in zip(('fr', 'en', 'zh'), keywords):
        kw += [f"(@id, {q(lang)}, {q(v)})" for v in values]
    out.append("insert into sorting_item_keyword (item_id, language_code, keyword) values "
               + ", ".join(kw) + ";")
    out.append("")

out.append("""-- The one instruction on the whole calendar that changes what a resident does
-- tonight: "Les bacs [...] doivent etre mis en bordure de rue avant 6 h, les
-- jours de collecte." It belongs on every generated occurrence, so it is
-- appended to each rule's note rather than left in a PDF.
update collection_schedule_rule set
    note_fr = concat(note_fr, ' Bacs en bordure de rue avant 6 h.'),
    note_en = concat(note_en, ' Bins at the curb before 6 a.m.'),
    note_zh = concat(note_zh, ' 收集日请在早上 6 点前将垃圾桶放到路边。');

-- Those notes are copied onto occurrences at generation time, so the ones
-- already materialized still carry the old text. Clearing the forward window
-- lets the top-up rewrite them; nothing before today is touched.
delete from collection_event where auto_generated = true and collection_date >= current_date;
update collection_schedule_rule set generated_through = date_sub(current_date, interval 1 day)
where generated_through > date_sub(current_date, interval 1 day);
""")

io.open('/home/user/blainville-waste-sorting/backend/src/main/resources/db/migration/V17__official_sorting_guide_items.sql',
        'w', encoding='utf-8').write("\n".join(out))
print("items:", len(ITEMS))
