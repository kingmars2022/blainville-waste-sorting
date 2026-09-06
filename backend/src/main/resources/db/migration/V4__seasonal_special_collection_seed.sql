insert into sorting_item (destination_type, bin_color, source_url)
values
('bulky', 'none', 'https://blainville.ca/services/environnement-et-voirie/encombrants'),
('special', 'none', 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles'),
('special', 'none', 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles'),
('special', 'none', 'https://blainville.ca/evenements/dechiquetage-de-documents-personnels-3'),
('special', 'none', 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles');

insert into sorting_item_translation (item_id, language_code, name, instruction)
values
(11, 'fr', 'Encombrants', 'Sur demande seulement. Faire la demande au plus tard le dernier jour du mois precedent. Deposer les items en bordure de rue entre dimanche 18 h et lundi 7 h durant la semaine du deuxieme lundi du mois.'),
(11, 'en', 'Bulky items', 'Request only. Submit the request no later than the last day of the previous month. Place items curbside between Sunday 6:00 PM and Monday 7:00 AM during the week of the second Monday of the month.'),
(11, 'zh', '大件垃圾', '仅限提前申请。最晚在前一个月最后一天前申请。在当月第二个星期一那一周，周日 18:00 至周一 7:00 之间放到路边。'),
(12, 'fr', 'Branches', 'Collecte gratuite sur demande en mai, juin et octobre. Les branches doivent deja etre en bordure de rue avant la demande.'),
(12, 'en', 'Branches', 'Free request-based collection in May, June, and October. Branches must already be curbside before the request is submitted.'),
(12, 'zh', '树枝', '5 月、6 月和 10 月可申请免费收树枝。申请前树枝必须已经放在路边。'),
(13, 'fr', 'Sapins de Noel', 'Demande a partir de decembre. La collecte commence la premiere semaine de janvier et dure environ deux semaines sur tout le territoire.'),
(13, 'en', 'Christmas trees', 'Requests open in December. Collection starts in the first week of January and lasts about two weeks city-wide.'),
(13, 'zh', '圣诞树', '12 月开始申请。收集通常从 1 月第一周开始，全市约持续两周。'),
(14, 'fr', 'Dechiquetage de documents personnels', 'Service gratuit plusieurs fois par annee. Apporter une preuve d identite avec photo et adresse. Maximum de trois boites standard par adresse.'),
(14, 'en', 'Personal document shredding', 'Free service several times per year. Bring photo ID with your address. Maximum of three standard boxes per address.'),
(14, 'zh', '个人文件粉碎', '每年多次免费服务。需要带照片和住址的身份证明。每个地址最多三箱标准尺寸文件。'),
(15, 'fr', 'Retailles de cedre', 'Service gratuit avec Arbressence. Deposer les retailles au bord de l entree, en tas ou dans des sacs. La matiere doit etre composee a 100 % de feuillage.'),
(15, 'en', 'Cedar trimmings', 'Free service with Arbressence. Place trimmings at the edge of the driveway, in piles or bags. Material must be 100% foliage.'),
(15, 'zh', '雪松修剪枝叶', 'Arbressence 提供免费收集。把枝叶放在车道边，可以成堆或装袋。材料必须是 100% 枝叶。');

insert into sorting_item_keyword (item_id, language_code, keyword)
values
(11, 'fr', 'encombrants'), (11, 'fr', 'matelas'), (11, 'fr', 'meubles'), (11, 'en', 'bulky'), (11, 'en', 'mattress'), (11, 'en', 'furniture'), (11, 'zh', '大件'), (11, 'zh', '床垫'), (11, 'zh', '家具'),
(12, 'fr', 'branches'), (12, 'fr', 'mai'), (12, 'fr', 'juin'), (12, 'fr', 'octobre'), (12, 'en', 'branches'), (12, 'en', 'may'), (12, 'en', 'june'), (12, 'en', 'october'), (12, 'zh', '树枝'), (12, 'zh', '五月'), (12, 'zh', '六月'), (12, 'zh', '十月'),
(13, 'fr', 'sapin'), (13, 'fr', 'noel'), (13, 'en', 'christmas tree'), (13, 'en', 'january'), (13, 'zh', '圣诞树'), (13, 'zh', '一月'),
(14, 'fr', 'dechiquetage'), (14, 'fr', 'documents'), (14, 'en', 'shredding'), (14, 'en', 'documents'), (14, 'zh', '粉碎'), (14, 'zh', '文件'),
(15, 'fr', 'cedre'), (15, 'fr', 'retailles'), (15, 'en', 'cedar'), (15, 'en', 'trimmings'), (15, 'zh', '雪松'), (15, 'zh', '枝叶');
