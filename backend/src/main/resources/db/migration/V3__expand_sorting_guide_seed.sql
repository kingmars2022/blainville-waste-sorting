insert into sorting_item (destination_type, bin_color, source_url)
values
('organic', 'brown', 'https://blainville.ca/services/environnement-et-voirie/matieres-organiques'),
('organic', 'brown', 'https://blainville.ca/services/environnement-et-voirie/matieres-organiques'),
('recycling', 'blue', 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables'),
('recycling', 'blue', 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables'),
('garbage', 'black', 'https://blainville.ca/services/environnement-et-voirie/ordures-menageres'),
('ecocentre', 'none', 'https://blainville.ca/services/environnement-et-voirie/ecocentre');

insert into sorting_item_translation (item_id, language_code, name, instruction)
values
(5, 'fr', 'Residus verts', 'Feuilles, fleurs, mauvaises herbes, gazon et petites branches vont dans le bac brun ou dans des sacs en papier brun.'),
(5, 'en', 'Yard waste', 'Leaves, flowers, weeds, grass clippings, and small branches go in the brown bin or brown paper bags.'),
(5, 'zh', '庭院绿色废料', '树叶、花、杂草、草屑和小树枝可以放入棕桶或棕色纸袋。'),
(6, 'fr', 'Papiers et cartons souilles d aliments', 'Les boites a pizza, assiettes en carton et essuie-tout vont dans le bac brun s ils ne contiennent pas de plastique ou de cire.'),
(6, 'en', 'Food-soiled paper and cardboard', 'Pizza boxes, paper plates, and paper towels go in the brown bin if they contain no plastic or wax.'),
(6, 'zh', '被食物弄脏的纸和纸板', '披萨盒、纸盘和厨房纸如果没有塑料或蜡层，可以放入棕桶。'),
(7, 'fr', 'Contenants et emballages', 'Vider et rincer legerement les contenants avant de les mettre dans le bac bleu.'),
(7, 'en', 'Containers and packaging', 'Empty and lightly rinse containers before placing them in the blue bin.'),
(7, 'zh', '容器和包装', '容器倒空并简单冲洗后放入蓝桶。'),
(8, 'fr', 'Imprimes', 'Journaux, circulaires, enveloppes, feuilles et cahiers vont dans le bac bleu.'),
(8, 'en', 'Printed paper', 'Newspapers, flyers, envelopes, paper sheets, and notebooks go in the blue bin.'),
(8, 'zh', '印刷纸张', '报纸、传单、信封、纸张和笔记本放入蓝桶。'),
(9, 'fr', 'Ordures menageres', 'Utiliser le bac noir pour ce qui ne va pas dans les autres bacs et qui est refuse a l ecocentre.'),
(9, 'en', 'Household waste', 'Use the black bin for items that do not belong in other bins and are refused at the ecocentre.'),
(9, 'zh', '生活垃圾', '黑桶用于不能放入其他桶、也不能送去 ecocentre 的物品。'),
(10, 'fr', 'Articles a apporter a l ecocentre', 'Peinture, piles, electroniques, pneus, aerosols, huiles et polystyrene doivent etre apportes a l ecocentre.'),
(10, 'en', 'Items to bring to the ecocentre', 'Paint, batteries, electronics, tires, aerosols, oil, and polystyrene should be brought to the ecocentre.'),
(10, 'zh', '需要送去 ecocentre 的物品', '油漆、电池、电子产品、轮胎、喷雾罐、油类和泡沫塑料请送去 ecocentre。');

insert into sorting_item_keyword (item_id, language_code, keyword)
values
(5, 'fr', 'feuilles'), (5, 'fr', 'gazon'), (5, 'fr', 'branches'), (5, 'en', 'leaves'), (5, 'en', 'grass'), (5, 'en', 'branches'), (5, 'zh', '树叶'), (5, 'zh', '草屑'), (5, 'zh', '树枝'),
(6, 'fr', 'pizza'), (6, 'fr', 'essuie-tout'), (6, 'en', 'pizza'), (6, 'en', 'paper towel'), (6, 'zh', '披萨盒'), (6, 'zh', '厨房纸'),
(7, 'fr', 'contenant'), (7, 'fr', 'emballage'), (7, 'fr', 'carton'), (7, 'en', 'container'), (7, 'en', 'packaging'), (7, 'en', 'cardboard'), (7, 'zh', '容器'), (7, 'zh', '包装'), (7, 'zh', '纸箱'),
(8, 'fr', 'journal'), (8, 'fr', 'enveloppe'), (8, 'en', 'newspaper'), (8, 'en', 'envelope'), (8, 'zh', '报纸'), (8, 'zh', '信封'),
(9, 'fr', 'couches'), (9, 'fr', 'jouet brise'), (9, 'fr', 'vaisselle cassee'), (9, 'en', 'diaper'), (9, 'en', 'broken toy'), (9, 'en', 'broken dish'), (9, 'zh', '尿布'), (9, 'zh', '坏玩具'), (9, 'zh', '破碎餐具'),
(10, 'fr', 'peinture'), (10, 'fr', 'piles'), (10, 'fr', 'electroniques'), (10, 'en', 'paint'), (10, 'en', 'batteries'), (10, 'en', 'electronics'), (10, 'zh', '油漆'), (10, 'zh', '电池'), (10, 'zh', '电子产品');
