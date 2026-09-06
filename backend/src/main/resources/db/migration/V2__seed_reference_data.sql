insert into collection_event
(collection_date, sector, collection_type, bin_color, note_fr, note_en, note_zh, source_url)
values
('2026-08-27', 'all', 'organic', 'brown',
 'Les matieres organiques sont collectees le jeudi sur tout le territoire.',
 'Organics are collected on Thursday throughout the city.',
 '厨余和有机垃圾全市每周四收集。',
 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles'),
('2026-09-01', 'south', 'recycling', 'blue',
 'Collecte au sud du boulevard de la Seigneurie.',
 'Collection south of boulevard de la Seigneurie.',
 'boulevard de la Seigneurie 以南区域收集。',
 'https://blainville.ca/storage/app/media/Services/Environnement%20et%20voirie/Collectes/calendrier_collectes_2026.pdf'),
('2026-09-02', 'north', 'recycling', 'blue',
 'Collecte au nord du boulevard de la Seigneurie.',
 'Collection north of boulevard de la Seigneurie.',
 'boulevard de la Seigneurie 以北区域收集。',
 'https://blainville.ca/storage/app/media/Services/Environnement%20et%20voirie/Collectes/calendrier_collectes_2026.pdf');

insert into sorting_item (destination_type, bin_color, source_url)
values
('organic', 'brown', 'https://blainville.ca/storage/app/media/Services/Environnement%20et%20voirie/Collectes/calendrier_collectes_2026.pdf'),
('recycling', 'blue', 'https://blainville.ca/storage/app/media/Services/Environnement%20et%20voirie/Collectes/calendrier_collectes_2026.pdf'),
('garbage', 'black', 'https://blainville.ca/storage/app/media/Services/Environnement%20et%20voirie/Collectes/calendrier_collectes_2026.pdf'),
('special', 'none', 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles');

insert into sorting_item_translation (item_id, language_code, name, instruction)
values
(1, 'fr', 'Restes de fruits et legumes', 'Deposez-les dans le bac brun.'),
(1, 'en', 'Fruit and vegetable scraps', 'Place them in the brown bin.'),
(1, 'zh', '水果和蔬菜残渣', '请放入棕桶。'),
(2, 'fr', 'Bouteille de shampoing', 'Deposez le contenant dans le bac bleu.'),
(2, 'en', 'Shampoo bottle', 'Place the container in the blue bin.'),
(2, 'zh', '洗发水瓶', '请把容器放入蓝桶。'),
(3, 'fr', 'Jouet brise', 'Deposez-le dans le bac noir si aucun autre service ne l accepte.'),
(3, 'en', 'Broken toy', 'Place it in the black bin if no other service accepts it.'),
(3, 'zh', '损坏的玩具', '如果没有其他回收服务接收，请放入黑桶。'),
(4, 'fr', 'Documents personnels a dechiqueter', 'Utilisez les dates de dechiquetage annoncees par la Ville.'),
(4, 'en', 'Personal documents for shredding', 'Use the shredding dates announced by the city.'),
(4, 'zh', '需要粉碎的个人文件', '请使用市政府公布的个人文件粉碎服务日期。');

insert into sorting_item_keyword (item_id, language_code, keyword)
values
(1, 'fr', 'fruits'),
(1, 'fr', 'legumes'),
(1, 'en', 'fruit'),
(1, 'en', 'vegetable'),
(1, 'zh', '水果'),
(1, 'zh', '蔬菜'),
(2, 'fr', 'bouteille shampoing'),
(2, 'en', 'shampoo bottle'),
(2, 'zh', '洗发水瓶'),
(3, 'fr', 'jouet brise'),
(3, 'en', 'broken toy'),
(3, 'zh', '坏玩具'),
(4, 'fr', 'documents personnels'),
(4, 'en', 'personal documents'),
(4, 'zh', '个人文件');

