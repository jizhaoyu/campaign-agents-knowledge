update app_user
set password_hash = '{bcrypt}$2a$10$0gBCQ4f7A1bgj1xyVe3qh.9zFlVVFaQqP2hy1U1mE7ywi..CyJEC2',
    updated_at = current_timestamp
where username = 'admin'
  and password_hash = '{noop}admin123';

update app_user
set password_hash = '{bcrypt}$2a$10$anLOzgefmmAMrvOhQZTSOuzTfNaFEGCf1klWRe3XJw3VBDkG741V.',
    updated_at = current_timestamp
where username = 'user'
  and password_hash = '{noop}user123';

update app_user
set password_hash = '{bcrypt}$2a$10$PrrfavPDhrfuF9JVIkRMyOx/YEz4dHL9jousSoTpC2be8a98IKQAG',
    updated_at = current_timestamp
where username = 'support'
  and password_hash = '{noop}support123';

update app_user
set password_hash = '{bcrypt}$2a$10$H6GcIhChGxly.6.9bTaKve2jvRdE/s1F4oyLYHsUk2lCSABVj8Y4O',
    updated_at = current_timestamp
where username = 'approver'
  and password_hash = '{noop}approver123';
