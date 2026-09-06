import type { Language } from "../i18n/messages";

export type DestinationType = "organic" | "recycling" | "garbage" | "ecocentre" | "bulky" | "special";
export type BinColor = "brown" | "blue" | "black" | "none";

export type SortingGuideItem = {
  id: string;
  destination: DestinationType;
  binColor: BinColor;
  availability?: Record<Language, string>;
  location?: Record<Language, string>;
  names: Record<Language, string>;
  instruction: Record<Language, string>;
  examples: Record<Language, string[]>;
  keywords: Record<Language, string[]>;
  sourceUrl: string;
};

export const sortingGuide: SortingGuideItem[] = [
  {
    id: "food-scraps",
    destination: "organic",
    binColor: "brown",
    names: {
      fr: "Residus alimentaires",
      en: "Food scraps",
      zh: "食物残渣"
    },
    instruction: {
      fr: "Deposez les residus alimentaires crus, cuits ou perimes dans le bac brun. Les sacs de plastique, meme compostables, sont interdits.",
      en: "Place raw, cooked, or expired food scraps in the brown bin. Plastic bags, even compostable ones, are not accepted.",
      zh: "生的、熟的或过期的食物残渣放入棕桶。不要使用塑料袋，包括可堆肥塑料袋。"
    },
    examples: {
      fr: ["fruits", "legumes", "pain", "pates", "viande", "poisson", "coquilles d'oeufs", "cafe", "the"],
      en: ["fruit", "vegetables", "bread", "pasta", "meat", "fish", "egg shells", "coffee", "tea"],
      zh: ["水果", "蔬菜", "面包", "意面", "肉", "鱼", "蛋壳", "咖啡渣", "茶叶"]
    },
    keywords: {
      fr: ["fruit", "legume", "pain", "pate", "viande", "poisson", "oeuf", "cafe", "the", "nourriture"],
      en: ["fruit", "vegetable", "bread", "pasta", "meat", "fish", "egg", "coffee", "tea", "food"],
      zh: ["水果", "蔬菜", "面包", "意面", "肉", "鱼", "蛋壳", "咖啡", "茶", "食物"]
    },
    sourceUrl: "https://blainville.ca/services/environnement-et-voirie/matieres-organiques"
  },
  {
    id: "green-residue",
    destination: "organic",
    binColor: "brown",
    names: {
      fr: "Residus verts",
      en: "Yard waste",
      zh: "庭院绿色废料"
    },
    instruction: {
      fr: "Deposez les feuilles, fleurs, mauvaises herbes, gazon et petites branches dans le bac brun ou dans des sacs en papier brun.",
      en: "Place leaves, flowers, weeds, grass clippings, and small branches in the brown bin or brown paper bags.",
      zh: "树叶、花、杂草、草屑和小树枝可以放入棕桶或棕色纸袋。"
    },
    examples: {
      fr: ["feuilles", "fleurs", "gazon", "mauvaises herbes", "petites branches"],
      en: ["leaves", "flowers", "grass", "weeds", "small branches"],
      zh: ["树叶", "花", "草屑", "杂草", "小树枝"]
    },
    keywords: {
      fr: ["feuille", "fleur", "gazon", "herbe", "branche", "terre", "paille"],
      en: ["leaf", "leaves", "flower", "grass", "weed", "branch", "soil", "straw"],
      zh: ["树叶", "花", "草", "杂草", "树枝", "土", "稻草"]
    },
    sourceUrl: "https://blainville.ca/services/environnement-et-voirie/matieres-organiques"
  },
  {
    id: "soiled-paper-cardboard",
    destination: "organic",
    binColor: "brown",
    names: {
      fr: "Papiers et cartons souilles d'aliments",
      en: "Food-soiled paper and cardboard",
      zh: "被食物弄脏的纸和纸板"
    },
    instruction: {
      fr: "Deposez-les dans le bac brun s'ils ne contiennent pas d'agrafe, de plastique ou de cire.",
      en: "Place them in the brown bin if they do not contain staples, plastic, or wax.",
      zh: "如果没有订书钉、塑料或蜡层，可以放入棕桶。"
    },
    examples: {
      fr: ["boite a pizza", "assiette en carton", "essuie-tout", "mouchoir", "sac en papier"],
      en: ["pizza box", "paper plate", "paper towel", "tissue", "paper bag"],
      zh: ["披萨盒", "纸盘", "厨房纸", "纸巾", "纸袋"]
    },
    keywords: {
      fr: ["pizza", "assiette", "carton souille", "essuie-tout", "mouchoir", "papier", "sac papier"],
      en: ["pizza", "paper plate", "soiled cardboard", "paper towel", "tissue", "paper bag"],
      zh: ["披萨", "纸盘", "脏纸板", "厨房纸", "纸巾", "纸袋"]
    },
    sourceUrl: "https://blainville.ca/services/environnement-et-voirie/matieres-organiques"
  },
  {
    id: "containers-packaging",
    destination: "recycling",
    binColor: "blue",
    names: {
      fr: "Contenants et emballages",
      en: "Containers and packaging",
      zh: "容器和包装"
    },
    instruction: {
      fr: "Videz et rincez legerement les contenants avant de les mettre dans le bac bleu.",
      en: "Empty and lightly rinse containers before placing them in the blue bin.",
      zh: "容器倒空并简单冲洗后放入蓝桶。"
    },
    examples: {
      fr: ["pots", "contenants alimentaires", "boites en carton", "papier d'aluminium", "sacs de plastique"],
      en: ["jars", "food containers", "cardboard boxes", "aluminum foil", "plastic bags"],
      zh: ["罐子", "食品容器", "纸箱", "铝箔", "塑料袋"]
    },
    keywords: {
      fr: ["contenant", "emballage", "pot", "carton", "aluminium", "sac plastique", "bouteille"],
      en: ["container", "packaging", "jar", "cardboard", "aluminum", "plastic bag", "bottle"],
      zh: ["容器", "包装", "罐", "纸箱", "铝箔", "塑料袋", "瓶"]
    },
    sourceUrl: "https://blainville.ca/services/environnement-et-voirie/matieres-recuperables"
  },
  {
    id: "printed-paper",
    destination: "recycling",
    binColor: "blue",
    names: {
      fr: "Imprimes",
      en: "Printed paper",
      zh: "印刷纸张"
    },
    instruction: {
      fr: "Deposez les journaux, circulaires, enveloppes, feuilles et cahiers dans le bac bleu.",
      en: "Place newspapers, flyers, envelopes, sheets of paper, and notebooks in the blue bin.",
      zh: "报纸、传单、信封、纸张和笔记本放入蓝桶。"
    },
    examples: {
      fr: ["journaux", "circulaires", "enveloppes", "feuilles", "cahiers"],
      en: ["newspapers", "flyers", "envelopes", "paper sheets", "notebooks"],
      zh: ["报纸", "传单", "信封", "纸张", "笔记本"]
    },
    keywords: {
      fr: ["journal", "circulaire", "enveloppe", "feuille", "cahier", "papier"],
      en: ["newspaper", "flyer", "envelope", "paper", "notebook"],
      zh: ["报纸", "传单", "信封", "纸", "笔记本"]
    },
    sourceUrl: "https://blainville.ca/services/environnement-et-voirie/matieres-recuperables"
  },
  {
    id: "household-garbage",
    destination: "garbage",
    binColor: "black",
    names: {
      fr: "Ordures menageres",
      en: "Household waste",
      zh: "生活垃圾"
    },
    instruction: {
      fr: "Utilisez le bac noir seulement pour ce qui ne va pas dans les autres bacs et qui est refuse a l'ecocentre.",
      en: "Use the black bin only for items that do not belong in other bins and are refused at the ecocentre.",
      zh: "黑桶只用于不能放入其他桶、也不能送去 écocentre 的物品。"
    },
    examples: {
      fr: ["couches", "produits hygieniques", "jouets brises", "vaisselle cassee", "ampoules autres que DEL"],
      en: ["diapers", "hygiene products", "broken toys", "broken dishes", "non-LED light bulbs"],
      zh: ["尿布", "卫生用品", "损坏的玩具", "破碎餐具", "非 LED 灯泡"]
    },
    keywords: {
      fr: ["couche", "hygienique", "jouet", "vaisselle", "ampoule", "ordure"],
      en: ["diaper", "hygiene", "toy", "dish", "light bulb", "garbage"],
      zh: ["尿布", "卫生", "玩具", "餐具", "灯泡", "垃圾"]
    },
    sourceUrl: "https://blainville.ca/services/environnement-et-voirie/ordures-menageres"
  },
  {
    id: "ecocentre-special",
    destination: "ecocentre",
    binColor: "none",
    names: {
      fr: "Articles a apporter a l'ecocentre",
      en: "Items to bring to the ecocentre",
      zh: "需要送去 écocentre 的物品"
    },
    location: {
      fr: "Ecocentre, 302, rue Omer-DeSerres, Blainville, QC J7C 5N3.",
      en: "Ecocentre, 302 rue Omer-DeSerres, Blainville, QC J7C 5N3.",
      zh: "Écocentre：302 rue Omer-DeSerres, Blainville, QC J7C 5N3。"
    },
    instruction: {
      fr: "Apportez les residus dangereux, appareils electroniques, pneus, peinture, piles et gros materiaux a l'ecocentre.",
      en: "Bring hazardous waste, electronics, tires, paint, batteries, and large materials to the ecocentre.",
      zh: "危险废弃物、电子产品、轮胎、油漆、电池和大型材料请送去 écocentre。"
    },
    examples: {
      fr: ["peinture", "piles", "electroniques", "pneus", "aerosols", "huiles", "polystyrene"],
      en: ["paint", "batteries", "electronics", "tires", "aerosols", "oil", "polystyrene"],
      zh: ["油漆", "电池", "电子产品", "轮胎", "喷雾罐", "油类", "泡沫塑料"]
    },
    keywords: {
      fr: ["peinture", "pile", "electronique", "pneu", "aerosol", "huile", "styromousse", "polystyrene"],
      en: ["paint", "battery", "electronics", "tire", "aerosol", "oil", "styrofoam", "polystyrene"],
      zh: ["油漆", "电池", "电子", "轮胎", "喷雾", "油", "泡沫", "保丽龙"]
    },
    sourceUrl: "https://blainville.ca/services/environnement-et-voirie/ecocentre"
  },
  {
    id: "bulky-items",
    destination: "bulky",
    binColor: "none",
    availability: {
      fr: "Sur demande seulement. Faire la demande au plus tard le dernier jour du mois precedent. Deposer en bordure de rue entre dimanche 18 h et lundi 7 h durant la semaine du deuxieme lundi du mois.",
      en: "Request only. Submit the request no later than the last day of the previous month. Place items curbside between Sunday 6:00 PM and Monday 7:00 AM during the week of the second Monday of the month.",
      zh: "仅限提前申请。最晚要在前一个月最后一天前申请。大件垃圾应在当月第二个星期一那一周，周日 18:00 至周一 7:00 之间放到路边。"
    },
    location: {
      fr: "En bordure de rue, devant votre residence.",
      en: "Curbside, in front of your residence.",
      zh: "放在你家门前路边。"
    },
    names: {
      fr: "Encombrants",
      en: "Bulky items",
      zh: "大件垃圾"
    },
    instruction: {
      fr: "Utilisez ce service pour les gros rebuts acceptes, comme matelas, fauteuils, meubles de jardin, electromenagers, tapis roules et attaches, lavabo, toilette, vitre ou miroir casse.",
      en: "Use this service for accepted large items such as mattresses, armchairs, patio furniture, appliances, rolled and tied carpet, sinks, toilets, broken glass, or mirrors.",
      zh: "可用于符合条件的大件物品，例如床垫、扶手椅、庭院家具、家电、卷好并绑好的地毯、洗手盆、马桶、破玻璃或镜子。"
    },
    examples: {
      fr: ["matelas", "fauteuil", "meuble de jardin", "laveuse", "secheuse", "tapis", "lavabo", "toilette"],
      en: ["mattress", "armchair", "patio furniture", "washer", "dryer", "carpet", "sink", "toilet"],
      zh: ["床垫", "扶手椅", "庭院家具", "洗衣机", "烘干机", "地毯", "洗手盆", "马桶"]
    },
    keywords: {
      fr: ["encombrant", "matelas", "fauteuil", "meuble", "electromenager", "tapis", "lavabo", "toilette"],
      en: ["bulky", "mattress", "armchair", "furniture", "appliance", "carpet", "sink", "toilet"],
      zh: ["大件", "床垫", "沙发", "家具", "家电", "地毯", "洗手盆", "马桶"]
    },
    sourceUrl: "https://blainville.ca/services/environnement-et-voirie/encombrants"
  },
  {
    id: "branch-collection",
    destination: "special",
    binColor: "none",
    availability: {
      fr: "Collecte gratuite sur demande en mai, juin et octobre. Les branches doivent deja etre en bordure de rue avant la demande.",
      en: "Free request-based collection in May, June, and October. Branches must already be curbside before the request is submitted.",
      zh: "5 月、6 月和 10 月可申请免费收树枝。申请前树枝必须已经放在路边。"
    },
    location: {
      fr: "En bordure de rue, sur votre terrain. Depot gratuit possible a l'ecocentre.",
      en: "Curbside on your property. Free drop-off is also available at the ecocentre.",
      zh: "放在你家地界内靠路边的位置。也可以免费送到 Écocentre。"
    },
    names: {
      fr: "Branches",
      en: "Branches",
      zh: "树枝"
    },
    instruction: {
      fr: "Les branches ne doivent pas depasser 15 cm de diametre et 2 m de longueur. Les souches doivent etre retirees. Les petites branches peuvent aussi aller au bac brun.",
      en: "Branches must not exceed 15 cm in diameter and 2 m in length. Stumps must be removed. Small branches can also go in the brown bin.",
      zh: "树枝直径不能超过 15 cm，长度不能超过 2 m，树桩要去掉。很小的树枝也可以走棕桶。"
    },
    examples: {
      fr: ["branches non attachees", "petites branches", "residus de taille"],
      en: ["loose branches", "small branches", "pruning waste"],
      zh: ["未捆绑树枝", "小树枝", "修剪下来的树枝"]
    },
    keywords: {
      fr: ["branche", "branches", "taille", "arbuste"],
      en: ["branch", "branches", "pruning", "shrub"],
      zh: ["树枝", "修剪", "灌木"]
    },
    sourceUrl: "https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles"
  },
  {
    id: "christmas-trees",
    destination: "special",
    binColor: "none",
    availability: {
      fr: "Demande a partir de decembre. La collecte commence la premiere semaine de janvier et dure environ deux semaines sur tout le territoire.",
      en: "Requests open in December. Collection starts in the first week of January and lasts about two weeks city-wide.",
      zh: "12 月开始申请。收集通常从 1 月第一周开始，全市约持续两周。"
    },
    location: {
      fr: "En bordure de rue, devant votre residence. Depot gratuit possible a l'ecocentre.",
      en: "Curbside, in front of your residence. Free drop-off is also available at the ecocentre.",
      zh: "放在你家门前路边。也可以免费送到 Écocentre。"
    },
    names: {
      fr: "Sapins de Noel",
      en: "Christmas trees",
      zh: "圣诞树"
    },
    instruction: {
      fr: "Deposez le sapin en bordure de rue avant 7 h le premier jour de collecte. Retirez toutes les decorations et n'utilisez pas de sac de plastique.",
      en: "Place the tree curbside before 7:00 AM on the first collection day. Remove all decorations and do not use a plastic bag.",
      zh: "请在收集第一天早上 7 点前把树放到路边。必须移除所有装饰，不要套塑料袋。"
    },
    examples: {
      fr: ["sapin naturel", "arbre de Noel sans decoration"],
      en: ["natural tree", "undecorated Christmas tree"],
      zh: ["天然圣诞树", "没有装饰的圣诞树"]
    },
    keywords: {
      fr: ["sapin", "noel", "arbre de noel"],
      en: ["christmas tree", "tree"],
      zh: ["圣诞树", "树"]
    },
    sourceUrl: "https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles"
  },
  {
    id: "personal-document-shredding",
    destination: "special",
    binColor: "none",
    availability: {
      fr: "Service gratuit plusieurs fois par annee. Prochaine date affichee par la Ville: 20 septembre 2026, 8 h a 16 h 30, a l'ecocentre.",
      en: "Free service several times per year. City-listed next date: September 20, 2026, 8:00 AM to 4:30 PM, at the ecocentre.",
      zh: "每年多次免费服务。市政府当前列出的下一次日期：2026 年 9 月 20 日，8:00-16:30，地点在 Écocentre。"
    },
    location: {
      fr: "Ecocentre, 302, rue Omer-DeSerres, Blainville, QC J7C 5N3.",
      en: "Ecocentre, 302 rue Omer-DeSerres, Blainville, QC J7C 5N3.",
      zh: "Écocentre：302 rue Omer-DeSerres, Blainville, QC J7C 5N3。"
    },
    names: {
      fr: "Dechiquetage de documents personnels",
      en: "Personal document shredding",
      zh: "个人文件粉碎"
    },
    instruction: {
      fr: "Presentez une preuve d'identite avec photo et adresse de residence. Maximum de trois boites standard par adresse.",
      en: "Bring photo ID with your residential address. Maximum of three standard boxes per address.",
      zh: "需要出示带照片和住址的身份证明。每个地址最多三箱标准尺寸文件。"
    },
    examples: {
      fr: ["releves bancaires", "documents fiscaux", "documents personnels"],
      en: ["bank statements", "tax documents", "personal papers"],
      zh: ["银行账单", "税务文件", "个人文件"]
    },
    keywords: {
      fr: ["dechiquetage", "documents", "papier confidentiel", "releves"],
      en: ["shredding", "documents", "confidential paper", "statements"],
      zh: ["粉碎", "文件", "个人文件", "账单"]
    },
    sourceUrl: "https://blainville.ca/evenements/dechiquetage-de-documents-personnels-3"
  },
  {
    id: "cedar-trimmings",
    destination: "special",
    binColor: "none",
    availability: {
      fr: "Service gratuit avec Arbressence. Le ramassage est fait dans les 48 heures ouvrables apres communication.",
      en: "Free service with Arbressence. Pickup is made within 48 business hours after contacting them.",
      zh: "Arbressence 提供免费收集。联系后通常在 48 个工作小时内收取。"
    },
    location: {
      fr: "Au bord de votre entree, en tas ou dans des sacs, puis communiquer avec Arbressence.",
      en: "At the edge of your driveway, in piles or bags, then contact Arbressence.",
      zh: "放在自家车道边，可以成堆或装袋，然后联系 Arbressence。"
    },
    names: {
      fr: "Retailles de cedre",
      en: "Cedar trimmings",
      zh: "雪松修剪枝叶"
    },
    instruction: {
      fr: "Deposez les retailles au bord de l'entree, en tas ou dans des sacs. La matiere doit etre composee a 100 % de feuillage.",
      en: "Place trimmings at the edge of the driveway, in piles or bags. Material must be 100% foliage.",
      zh: "把修剪下来的雪松枝叶放在车道边，可以成堆或装袋。材料必须是 100% 枝叶。"
    },
    examples: {
      fr: ["feuillage de cedre", "retailles de haie de cedre"],
      en: ["cedar foliage", "cedar hedge trimmings"],
      zh: ["雪松叶", "雪松篱笆修剪物"]
    },
    keywords: {
      fr: ["cedre", "retailles", "haie"],
      en: ["cedar", "trimmings", "hedge"],
      zh: ["雪松", "枝叶", "篱笆"]
    },
    sourceUrl: "https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles"
  }
];
