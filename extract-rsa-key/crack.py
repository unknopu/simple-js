from Crypto.PublicKey import RSA

public_key = RSA.importKey(open('public.key', 'r').read())

e, n = public_key.e, public_key.n
factor = "66 786253 706550 106651 546922 109281 130018 108580 330137 737023 063153 215868 656799 140830 401717 687979 014370 575432 349299 822348 593480 105855 792805 572771 414208 174453 015865 282142 790897 714037 523138 606055 539556 245525 288537 575545 283847 375133 918614 361476 148858 960383 966021 995875 312158 222430 892437 413254 837113 655891 077705 651117 = 8172 285708 817950 195609 942788 528777 311997 560168 630579 888192 183660 269368 178285 165655 478925 615144 750734 205408 878496 249047 888681 766674 672936 214471 503035 969937 × 8172 285708 817950 195609 942788 528777 311997 560168 630579 888192 183660 269368 178285 165655 478925 615144 750734 205408 878496 249047 888681 766674 672936 214471 503035 970141"
p, q = factor.split('=')[1].replace(" ", "").split("×")
phi = n-(int(p)+int(q)-1)

from Crypto.Util.number import inverse
d = inverse(e, phi)

privateKey = RSA.construct((n, e, d))
privateKeyPem = privateKey.exportKey(pkcs=8)

print("\n", privateKeyPem.decode('utf8'), "\n")