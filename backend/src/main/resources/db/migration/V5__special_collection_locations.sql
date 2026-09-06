alter table sorting_item_translation
    add column location varchar(500) null after instruction;

update sorting_item_translation
set location = case language_code
    when 'fr' then 'Ecocentre, 302, rue Omer-DeSerres, Blainville, QC J7C 5N3.'
    when 'en' then 'Ecocentre, 302 rue Omer-DeSerres, Blainville, QC J7C 5N3.'
    when 'zh' then 'Écocentre：302 rue Omer-DeSerres, Blainville, QC J7C 5N3。'
end
where item_id in (10, 14);

update sorting_item_translation
set location = case language_code
    when 'fr' then 'En bordure de rue, devant votre residence.'
    when 'en' then 'Curbside, in front of your residence.'
    when 'zh' then '放在你家门前路边。'
end
where item_id in (11, 13);

update sorting_item_translation
set location = case language_code
    when 'fr' then 'En bordure de rue, sur votre terrain. Depot gratuit possible a l ecocentre.'
    when 'en' then 'Curbside on your property. Free drop-off is also available at the ecocentre.'
    when 'zh' then '放在你家地界内靠路边的位置。也可以免费送到 Écocentre。'
end
where item_id = 12;

update sorting_item_translation
set location = case language_code
    when 'fr' then 'Au bord de votre entree, en tas ou dans des sacs, puis communiquer avec Arbressence.'
    when 'en' then 'At the edge of your driveway, in piles or bags, then contact Arbressence.'
    when 'zh' then '放在自家车道边，可以成堆或装袋，然后联系 Arbressence。'
end
where item_id = 15;
